package com.eed3si9n.jarjarabrams

import java.io.{
  BufferedInputStream,
  BufferedOutputStream,
  FileInputStream,
  FileOutputStream,
  InputStream,
  IOException,
  OutputStream
}
import java.nio.file.{ Files, Path }
import java.util.jar.{ JarFile, JarOutputStream }
import java.util.zip.ZipInputStream
import scala.util.control.NonFatal
import scala.reflect.ClassTag

abstract class Using[Source, A] {
  protected def open(src: Source): A
  def apply[R](src: Source)(f: A => R): R = {
    val resource = open(src)
    try {
      f(resource)
    } finally {
      close(resource)
    }
  }
  protected def close(out: A): Unit
}

object Using {
  def fileOutputStream(append: Boolean): Using[Path, OutputStream] =
    file(f => new BufferedOutputStream(new FileOutputStream(f.toFile, append)))
  val jarOutputStream: Using[Path, JarOutputStream] =
    file(f => new JarOutputStream(new BufferedOutputStream(new FileOutputStream(f.toFile))))
  val fileInputStream: Using[Path, InputStream] =
    file(f => new BufferedInputStream(new FileInputStream(f.toFile)))
  val jarFile: Using[Path, JarFile] =
    file(f => new JarFile(f.toFile))
  val zipInputStream = wrap((in: InputStream) => new ZipInputStream(in))

  def file[A1 <: AutoCloseable](action: Path => A1): Using[Path, A1] =
    file(action, closeCloseable)

  def file[A1](action: Path => A1, closeF: A1 => Unit): Using[Path, A1] =
    new OpenFile[A1] {
      def openImpl(file: Path) = action(file)
      def close(a: A1) = closeF(a)
    }

  def wrap[Source: ClassTag, A1 <: AutoCloseable: ClassTag](
      action: Source => A1
  ): Using[Source, A1] =
    wrap(action, closeCloseable)

  def wrap[Source: ClassTag, A1: ClassTag](
      action: Source => A1,
      closeF: A1 => Unit
  ): Using[Source, A1] =
    new WrapUsing[Source, A1] {
      def openImpl(source: Source) = action(source)
      def close(a: A1) = closeF(a)
    }

  private def closeCloseable[A1 <: AutoCloseable]: A1 => Unit = _.close()

  private abstract class WrapUsing[Source: ClassTag, A1: ClassTag] extends Using[Source, A1] {
    protected def label[A: ClassTag] = implicitly[ClassTag[A]].runtimeClass.getSimpleName
    protected def openImpl(source: Source): A1
    protected final def open(source: Source): A1 =
      try {
        openImpl(source)
      } catch {
        case NonFatal(e) =>
          throw new RuntimeException(s"error wrapping ${label[Source]} in ${label[A1]}", e)
      }
  }

  private trait OpenFile[A] extends Using[Path, A] {
    protected def openImpl(file: Path): A
    protected final def open(file: Path): A = {
      val parent = file.getParent
      if (parent != null) {
        try Files.createDirectories(parent)
        catch { case _: IOException => }
      }
      openImpl(file)
    }
  }
}
