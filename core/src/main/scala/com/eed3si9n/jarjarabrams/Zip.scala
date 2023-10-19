package com.eed3si9n.jarjarabrams

import com.eed3si9n.jarjar.util.{ DuplicateJarEntryException, EntryStruct }
import java.nio.file.{ Files, NoSuchFileException, Path, StandardCopyOption }
import java.nio.file.attribute.FileTime
import java.io.{ ByteArrayOutputStream, FileNotFoundException, InputStream, OutputStream }
import java.security.MessageDigest
import java.util.jar.JarEntry
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable

object Zip {

  /** The size of the byte or char buffer used in various methods. */
  private final val BufferSize = 8192
  // 2010-01-01
  private final val default2010Timestamp = 1262304000000L
  // ZIP timestamps have a resolution of 2 seconds.
  // see http://www.info-zip.org/FAQ.html#limits
  final private val minimumTimestampIncrement = 2000L
  // 1980-01-03
  // Technically it should be 1980-01-01, but Java checks against
  // the year segment in localtime and ends up capturing timezone,
  // so to be safe we should consider Jan 3 to be the minimum.
  //
  //   $ unzip -l example/byte-buddy-agent.jar | head
  // Archive:  example/byte-buddy-agent.jar
  //   Length      Date    Time    Name
  // ---------  ---------- -----   ----
  //         0  00-00-1980 04:08   META-INF/
  //       988  00-00-1980 04:08   META-INF/MANIFEST.MF
  private final val minimumTimestamp = 315705600000L

  def list(inputJar: Path): List[(String, Long)] =
    Using.jarFile(inputJar) { in =>
      in.entries.asScala
        .map(entry => (entry.getName, entry.getTime))
        .toList
    }

  /**
   * Treating JAR file as a set of EntryStruct, this implements a
   * functional processing, intended for in-memory shading.
   */
  def transformJarFile(
      inputJar: Path,
      outputJar: Path,
      resetTimestamp: Boolean,
      warnOnDuplicateClass: Boolean
  )(f: EntryStruct => Option[EntryStruct]): Path =
    Using.jarFile(inputJar) { in =>
      val tempJar = Files.createTempFile("jarjar", ".jar")
      Using.jarOutputStream(tempJar) { out =>
        val names = new mutable.HashSet[String]
        in.entries.asScala.foreach { entry0 =>
          val struct0 = entryStruct(
            entry0.getName,
            entry0.getTime,
            toByteArray(in.getInputStream(entry0)),
            skipTransform = false
          )
          f(struct0) match {
            case Some(struct) =>
              if (names.add(struct.name)) {
                val entry = new JarEntry(struct.name)
                val time =
                  if (resetTimestamp) hardcodedZipTimestamp(struct.name)
                  else enforceMinimum(struct.time)
                entry.setTime(time)
                entry.setCompressedSize(-1)
                out.putNextEntry(entry)
                out.write(struct.data)
              } else if (struct.name.endsWith("/")) ()
              else {
                if (warnOnDuplicateClass)
                  Console.err.println(
                    s"in ${inputJar}, found duplicate files with name: ${struct.name}, ignoring due to specified option"
                  )
                else throw new DuplicateJarEntryException(inputJar.toString, struct.name)
              }
            case None => ()
          }
        }
      }
      Files.move(tempJar, outputJar, StandardCopyOption.REPLACE_EXISTING)
      resetModifiedTime(outputJar)
      outputJar
    }

  private val localized2010Timestamp = localizeTimestamp(default2010Timestamp)
  private val localized2010TimestampPlus2s = localizeTimestamp(
    default2010Timestamp + minimumTimestampIncrement
  )
  private val localizedMinimumTimestamp = localizeTimestamp(minimumTimestamp)

  /**
   * Returns the normalized timestamp for a jar entry based on its name.
   * This is necessary supposedly since javac will, when loading a class X,
   * prefer a source file to a class file, if both files have the same timestamp. Therefore, we need
   * to adjust the timestamp for class files to slightly after the normalized time.
   */
  private def hardcodedZipTimestamp(name: String): Long =
    if (name.endsWith(".class")) localized2010TimestampPlus2s
    else localized2010Timestamp

  private def enforceMinimum(timestampInLocal: Long): Long =
    if (timestampInLocal < localizedMinimumTimestamp) localizedMinimumTimestamp
    else timestampInLocal

  /*
   * The zip 'setTime' methods try to convert from the given time to the local time based
   * on java.util.TimeZone.getDefault(). When explicitly specifying the timestamp, we assume
   * this already has been done if desired, so we need to 'convert back' here.
   */
  private def localizeTimestamp(timestampInUtc: Long): Long =
    timestampInUtc - java.util.TimeZone.getDefault().getOffset(timestampInUtc)

  def entryStruct(
      name: String,
      time: Long,
      data: Array[Byte],
      skipTransform: Boolean = false
  ): EntryStruct = {
    val struct = new EntryStruct()
    struct.name = name
    struct.time = time
    struct.data = data
    struct.skipTransform = skipTransform
    struct
  }

  def resetModifiedTime(file: Path): Boolean =
    try {
      setModifiedTime(file, default2010Timestamp)
      true
    } catch {
      case _: FileNotFoundException =>
        false
    }

  def setModifiedTimeOrFalse(file: Path, mtime: Long): Boolean =
    try {
      setModifiedTime(file, mtime)
      true
    } catch {
      case _: FileNotFoundException =>
        false
    }

  def setModifiedTime(filePath: Path, mtime: Long): Unit =
    mapNoSuchFileException {
      Files.setLastModifiedTime(filePath, FileTime.fromMillis(mtime))
      ()
    }

  private def mapNoSuchFileException[A](f: => A): A =
    try {
      f
    } catch {
      case e: NoSuchFileException => throw new FileNotFoundException(e.getFile).initCause(e)
    }

  def transfer(in: Path, out: OutputStream): Unit =
    Using.fileInputStream(in)(in => transfer(in, out))

  def transfer(in: InputStream, out: OutputStream): Unit = transferImpl(in, out, false)

  private def transferImpl(in: InputStream, out: OutputStream, close: Boolean) = {
    try {
      val buffer = new Array[Byte](BufferSize)
      @tailrec def read(): Unit = {
        val byteCount = in.read(buffer)
        if (byteCount >= 0) {
          out.write(buffer, 0, byteCount)
          read()
        }
      }
      read()
    } finally {
      if (close) in.close
    }
  }

  private def toByteArray(in: InputStream): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    transfer(in, baos)
    baos.toByteArray()
  }

  def createDirectories(p: Path): Unit =
    if (Files.exists(p)) ()
    else Files.createDirectories(p)

  def sha256(p: Path): String =
    Using.fileInputStream(p) { in0 =>
      val digest = MessageDigest.getInstance("SHA-256")
      Using.digestInputStream(digest)(in0) { in =>
        val out: OutputStream = (b: Int) => ()
        transfer(in, out)
      }
      toHexString(digest.digest())
    }

  private def toHexString(bytes: Array[Byte]): String = {
    val buffer = new StringBuilder(bytes.length * 2)
    for { i <- bytes.indices } {
      val hex = Integer.toHexString(bytes(i) & 0xff)
      if (hex.length() == 1) {
        buffer.append('0')
      }
      buffer.append(hex)
    }
    buffer.toString
  }
}
