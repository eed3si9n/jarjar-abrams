package com.eed3si9n.jarjarabrams

import com.eed3si9n.jarjar.util.EntryStruct
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, NoSuchFileException, Path }
import java.nio.file.attribute.FileTime
import java.io.{
  BufferedOutputStream,
  ByteArrayOutputStream,
  File,
  FileNotFoundException,
  InputStream,
  OutputStream,
}
import java.util.jar.{ Attributes, JarEntry, JarFile, JarOutputStream, Manifest }
import java.util.zip.{ CRC32, ZipEntry, ZipInputStream, ZipOutputStream }
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.immutable.TreeSet
import scala.collection.mutable
import scala.util.control.NonFatal

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
  private final val minimumTimestamp = 315705600L

  def jar(sources: Iterable[(Path, String)], outputJar: Path): Unit =
    archive(sources.toSeq, outputJar, None, default2010Timestamp, jar = true)

  def unjar(from: Path, toDirectory: Path): Set[Path] =
    Using.fileInputStream(from)(in => unzipStream(in, toDirectory))

  def list(inputJar: Path): List[(String, Long)] =
    Using.jarFile(inputJar) { in =>
      in.entries.asScala.toList.map { entry =>
        (entry.getName, entry.getTime)
      }
    }

  /**
   * Treating JAR file as a set of EntryStruct, this implements a
   * functional processing, intended for in-memory shading.
   */
  def flatMap(
      inputJar: Path,
      outputJar: Path,
      resetTimestamp: Boolean
  )(f: EntryStruct => Option[EntryStruct]): Path =
    Using.jarFile(inputJar) { in =>
      val tempJar = Files.createTempFile("jarjar", ".jar")
      val names = new mutable.HashSet[String]
      Using.jarOutputStream(tempJar) { out =>
        in.entries.asScala.toList.foreach { entry0 =>
          val struct0 = entryStruct(
            entry0.getName,
            entry0.getTime, {
              val baos = new ByteArrayOutputStream()
              transfer(in.getInputStream(entry0), baos)
              baos.toByteArray()
            },
            false,
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
                // duplicate entry
                // throw?
              }
            case None => ()
          }
        }
      }
      if (Files.exists(outputJar)) {
        Files.delete(outputJar)
      }
      Files.copy(tempJar, outputJar)
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
      skipTransform: Boolean = false,
  ): EntryStruct = {
    val struct = new EntryStruct()
    struct.name = name
    struct.time = time
    struct.data = data
    struct.skipTransform = skipTransform
    struct
  }

  private def archive(
      mapping: Seq[(Path, String)],
      outputFile: Path,
      manifest: Option[Manifest],
      time: Long,
      jar: Boolean,
  ) = {
    val localTime = localizeTimestamp(time)
    if (Files.isDirectory(outputFile))
      sys.error(s"specified output file $outputFile is a directory.")
    else {
      val outputDir = outputFile.getParent
      createDirectories(outputDir)
      withZipOutput(outputFile, manifest, localTime, jar) { output =>
        val createEntry: (String => ZipEntry) =
          if (jar) new JarEntry(_)
          else new ZipEntry(_)
        writeZip(mapping, output, localTime)(createEntry)
      }
    }
  }

  private def writeZip(sources: Seq[(Path, String)], output: ZipOutputStream, time: Long)(
      createEntry: String => ZipEntry
  ) = {
    val files = sources
      .flatMap {
        case (file, name) =>
          if (Files.isRegularFile(file)) (file, normalizeToSlash(name)) :: Nil
          else Nil
      }
      .sortBy {
        case (_, name) =>
          name
      }

    // The CRC32 for an empty value, needed to store directories in zip files
    val emptyCRC = new CRC32().getValue()

    def addDirectoryEntry(name: String) = {
      output putNextEntry makeDirectoryEntry(name)
      output.closeEntry()
    }

    def makeDirectoryEntry(name: String) = {
      val e = createEntry(name)
      e.setTime(time)
      e.setSize(0)
      e.setMethod(ZipEntry.STORED)
      e.setCrc(emptyCRC)
      e
    }

    def makeFileEntry(file: Path, name: String) = {
      val e = createEntry(name)
      e.setTime(time)
      e
    }
    def addFileEntry(file: Path, name: String) = {
      output putNextEntry makeFileEntry(file, name)
      transfer(file, output)
      output.closeEntry()
    }

    // Calculate directories and add them to the generated Zip
    allDirectoryPaths(files).foreach(addDirectoryEntry(_))

    // Add all files to the generated Zip
    files.foreach { case (file, name) => addFileEntry(file, name) }
  }

  private def normalizeToSlash(name: String) = {
    val sep = File.separatorChar
    if (sep == '/') name else name.replace(sep, '/')
  }

  // map a path a/b/c to List("a", "b")
  private def relativeComponents(path: String): List[String] =
    path.split("/").toList.dropRight(1)

  // map components List("a", "b", "c") to List("a/b/c/", "a/b/", "a/", "")
  private def directories(path: List[String]): List[String] =
    path.foldLeft(List(""))((e, l) => (e.head + l + "/") :: e)

  // map a path a/b/c to List("a/b/", "a/")
  private def directoryPaths(path: String): List[String] =
    directories(relativeComponents(path)).filter(_.length > 1)

  // produce a sorted list of all the subdirectories of all provided files
  private def allDirectoryPaths(files: Iterable[(Path, String)]) =
    TreeSet[String]() ++ (files.flatMap { case (_, name) => directoryPaths(name) })

  private def withZipOutput(file: Path, manifest: Option[Manifest], time: Long, jar: Boolean)(
      f: ZipOutputStream => Unit
  ) = {
    Using.fileOutputStream(false)(file) { fileOut =>
      val (zipOut, _) =
        manifest match {
          case Some(mf) =>
            import Attributes.Name.MANIFEST_VERSION
            val main = mf.getMainAttributes
            if (!main.containsKey(MANIFEST_VERSION))
              main.put(MANIFEST_VERSION, "1.0")
            val os = new JarOutputStream(fileOut)
            val e = new ZipEntry(JarFile.MANIFEST_NAME)
            e.setTime(time)
            os.putNextEntry(e)
            mf.write(new BufferedOutputStream(os))
            os.closeEntry()
            (os, "jar")
          case None =>
            if (jar) (new JarOutputStream(fileOut), "jar")
            else (new ZipOutputStream(fileOut, StandardCharsets.UTF_8), "zip")
        }
      try {
        f(zipOut)
      } finally {
        zipOut.close
      }
    }
  }

  private def unzipStream(
      from: InputStream,
      toDirectory: Path,
      preserveLastModified: Boolean = false
  ): Set[Path] = {
    createDirectories(toDirectory)
    Using.zipInputStream(from)(zipInput => extract(zipInput, toDirectory, preserveLastModified))
  }

  private def extract(
      from: ZipInputStream,
      toDirectory: Path,
      preserveLastModified: Boolean
  ) = {
    val set = new mutable.HashSet[Path]
    @tailrec def next(): Unit = {
      val entry = from.getNextEntry
      if (entry == null) ()
      else {
        val name = entry.getName
        val target = toDirectory.resolve(name)
        if (entry.isDirectory) createDirectories(target)
        else {
          set += target
          try {
            Using.fileOutputStream(false)(target)(out => transfer(from, out))
          } catch {
            case NonFatal(e) =>
              throw new RuntimeException(
                "error extracting Zip entry '" + name + "' to '" + target + "'",
                e
              )
          }
        }
        if (preserveLastModified) setModifiedTimeOrFalse(target, entry.getTime)
        else resetModifiedTime(target)

        from.closeEntry()
        next()
      }
    }
    next()
    Set() ++ set
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

  def createDirectories(p: Path): Unit =
    if (Files.exists(p)) ()
    else Files.createDirectories(p)
}
