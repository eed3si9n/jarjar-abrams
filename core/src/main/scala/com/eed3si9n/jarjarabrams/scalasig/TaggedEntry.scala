package com.eed3si9n.jarjarabrams
package scalasig

import java.io.ByteArrayOutputStream

import scala.reflect.internal.pickling.PickleFormat

sealed trait TaggedEntry {
  def tag: Int
  def toBytes: Array[Byte] // Entry payload. Does not include the tag or length.
}

object TaggedEntry {

  /**
   * Unparsed entry
   */
  case class RawEntry(tag: Int, bytes: Seq[Byte]) extends TaggedEntry {
    override def toBytes: Array[Byte] = bytes.toArray
  }

  /**
   * Named entry. Either a term name or type name
   */
  case class NameEntry(tag: Int, name: String) extends TaggedEntry {
    override def toBytes: Array[Byte] = name.getBytes("UTF-8")
  }

  /**
   * Ref entry. Either a EXTMODCLASSref or EXTref
   */
  case class RefEntry(tag: Int, nameRef: Int, ownerRef: Option[Int]) extends TaggedEntry {

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

  def apply(tag: Int, bytes: Array[Byte]): TaggedEntry = {
    tag match {
      case tag @ (PickleFormat.TERMname | PickleFormat.TYPEname) =>
        NameEntry(tag, new String(bytes, "UTF-8"))
      case tag @ (PickleFormat.EXTMODCLASSref | PickleFormat.EXTref) =>
        val r = new ByteArrayReader(bytes)
        val nameRef = r.readNat()
        val symbolRef = if (r.atEnd) None else Some(r.readNat())
        RefEntry(tag, nameRef, symbolRef)
      case _ =>
        RawEntry(tag, bytes)
    }
  }
}
