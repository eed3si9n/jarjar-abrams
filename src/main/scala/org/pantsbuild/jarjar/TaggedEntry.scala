package org.pantsbuild.jarjar

import java.io.ByteArrayOutputStream

import scala.reflect.internal.pickling.PickleFormat

trait TaggedEntry {
  def tag: Int
  def toBytes: Array[Byte]  // Entry payload. Does not include the tag or length.
}
case class RawEntry(tag: Int, bytes: Array[Byte]) extends TaggedEntry {
  override def toBytes: Array[Byte] = bytes
}

abstract class Name(val tag: Int) extends TaggedEntry {
  val name: String

  override def toBytes: Array[Byte] = name.getBytes("UTF-8")
}

case class TermName(name: String) extends Name(PickleFormat.TERMname)
case class TypeName(name: String) extends Name(PickleFormat.TYPEname)

abstract class Ref(val tag: Int) extends TaggedEntry {
  val nameRef: Int
  val ownerRef: Option[Int]

  def update(newNameRef: Int, newOwnerRef: Option[Int]): Ref

  override def toBytes: Array[Byte] = {
    val os = new ByteArrayOutputStream()
    val writer = new Nat.Writer {
      override def writeByte(b: Int): Unit = os.write(b)
    }

    ownerRef match {
      case Some(owner) =>
        writer.writeNat(nameRef)
        writer.writeNat(owner)
      case None =>
        writer.writeNat(nameRef)
    }

    os.toByteArray
  }
}

case class ExtModClassRef(nameRef: Int, ownerRef: Option[Int]) extends Ref(PickleFormat.EXTMODCLASSref) {
  def update(newNameRef: Int, newOwnerRef: Option[Int]): Ref = ExtModClassRef(newNameRef, newOwnerRef)
}
case class ExtRef(nameRef: Int, ownerRef: Option[Int]) extends Ref(PickleFormat.EXTref) {
  def update(newNameRef: Int, newOwnerRef: Option[Int]): Ref = ExtRef(newNameRef, newOwnerRef)
}

object TaggedEntry {
  def apply(tag: Int, bytes: Array[Byte]): TaggedEntry = {
    tag match {
      case PickleFormat.TERMname =>
        TermName(new String(bytes, "UTF-8"))
      case PickleFormat.TYPEname =>
        TypeName(new String(bytes, "UTF-8"))
      case PickleFormat.EXTMODCLASSref =>
        val r = new ByteArrayReader(bytes)
        val nameRef = r.readNat()
        val symbolRef = if (r.atEnd) None else Some(r.readNat())
        ExtModClassRef(nameRef, symbolRef)
      case PickleFormat.EXTref =>
        val r = new ByteArrayReader(bytes)
        val nameRef = r.readNat()
        val symbolRef = if (r.atEnd) None else Some(r.readNat())
        ExtRef(nameRef, symbolRef)
      case _ =>
        RawEntry(tag, bytes)
    }
  }
}