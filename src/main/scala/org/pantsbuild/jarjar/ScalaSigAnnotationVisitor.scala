package org.pantsbuild.jarjar

import org.objectweb.asm.{AnnotationVisitor, ClassVisitor, Opcodes}
import org.pantsbuild.jarjar.TableEntry._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.reflect.internal.pickling.{ByteCodecs, PickleBuffer, PickleFormat}

class ScalaSigClassVisitor(fileName: String, cv: ClassVisitor, rules: Seq[Rule]) extends ClassVisitor(Opcodes.ASM7, cv) {

  override def visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = {
    if (descriptor == "Lscala/reflect/ScalaSignature;") {
      new ScalaSigAnnotationVisitor(fileName, super.visitAnnotation(descriptor, visible), rules)
    } else if (descriptor == "Lscala/reflect/ScalaLongSignature;") {
      throw new RuntimeException("Shading ScalaLongSignature not implemented")
    } else {
      super.visitAnnotation(descriptor, visible)
    }
  }
}

class ScalaSigAnnotationVisitor(fileName: String, av: AnnotationVisitor, rules: Seq[Rule]) extends AnnotationVisitor(Opcodes.ASM7, av) {

  override def visit(name: String, value: Any): Unit = {

//    if (fileName.contains("EndpointsWithCustomErrors")) {
//      println(fileName)
//      println(Base64.getEncoder.encodeToString(value.asInstanceOf[String].getBytes("UTF-8")))
//    }

    val bytes = value.asInstanceOf[String].getBytes("UTF-8")
    val len = ByteCodecs.decode(bytes)
    var pb = new PickleBuffer(bytes, 0, len)

    val version = (pb.readNat(), pb.readNat())

    pb = new PickleBuffer(bytes, 0, len)
    val table = new EntryTable(pb.toIndexedSeq.map((TableEntry.apply _).tupled).toBuffer)
    table.renameEntries(rules)

    val newEntries = table.entries

    val newPb = new PickleBuffer(Array[Byte](4), 0, 0)
    newPb.writeNat(version._1)
    newPb.writeNat(version._2)
    newPb.writeNat(newEntries.size)
    newEntries.foreach(_.write(newPb))

    val newBytes = newPb.bytes.slice(0, newPb.writeIndex)
    val newValue = new String(ubytesToCharArray(mapToNextModSevenBits(scala.reflect.internal.pickling.ByteCodecs.encode8to7(newBytes))))

    super.visit(name, newValue)
  }

  // These encoding functions are copied from scala.reflect.internal.AnnotationInfos.ScalaSigBytes
  private def mapToNextModSevenBits(src: Array[Byte]): Array[Byte] = {
    var i = 0
    val srclen = src.length
    while (i < srclen) {
      val in = src(i)
      src(i) = if (in == 0x7f) 0.toByte else (in + 1).toByte
      i += 1
    }
    src
  }

  def ubytesToCharArray(bytes: Array[Byte]): Array[Char] = {
    val ca = new Array[Char](bytes.length)
    var idx = 0
    while(idx < bytes.length) {
      val b: Byte = bytes(idx)
      assert((b & ~0x7f) == 0)
      ca(idx) = b.asInstanceOf[Char]
      idx += 1
    }
    ca
  }

  override def visitAnnotation(name: String, descriptor: String): AnnotationVisitor = {
    super.visitAnnotation(name, descriptor)
  }

  override def visitArray(name: String): AnnotationVisitor = {
    super.visitArray(name)
  }

  override def visitEnd(): Unit = {
    super.visitEnd()
  }
}

class EntryTable(var entries: mutable.Buffer[TableEntry]) {
  private val termIndices: mutable.Map[String, Int] = mutable.HashMap(
    entries.zipWithIndex.collect {
      case (entry: TableEntry.TermName, index) => (entry.name, index)
    }:_*
  )

  private val typeIndices: mutable.Map[String, Int] = mutable.HashMap(
    entries.zipWithIndex.collect {
      case (entry: TableEntry.TypeName, index) => (entry.name, index)
    }:_*
  )

  def renameEntries(rules: Seq[Rule]): Unit = {
    val wildcards = PatternElement.createWildcards(rules.asJava).asScala
    def replaceHelper(value: String): Option[String] = {
      val result = wildcards.flatMap {
        wc =>
          // Hack to replace the package object name.
          Option(wc.replace(value)).orElse(Option(wc.replace(value + "/")).map(_.dropRight(1)))
      }.headOption

      result
    }

    entries.zipWithIndex.foreach {
      case (ref: Ref, index) =>
        entries(ref.nameRef) match {
          case _: TermName =>
            resolveRef(ref).foreach { name =>
              replaceHelper(name).foreach { replaced =>
                val parts = replaced.split('/')

                val myOwner = parts.init.foldLeft(Option.empty[Int]) { (owner, part) =>
                  val nextOwner = appendEntry(ExtModClassRef(getOrInsertTermName(part), owner))
                  Some(nextOwner)
                }

                entries(index) = ref.update(getOrInsertTermName(parts.last), myOwner)
              }
            }

          case _: TypeName =>
            resolveRef(ref).foreach { name =>
              replaceHelper(name).foreach { replaced =>
                val parts = replaced.split('/')

                val myOwner = parts.init.foldLeft(Option.empty[Int]) { (owner, part) =>
                  val nextOwner = appendEntry(ExtModClassRef(getOrInsertTermName(part), owner))
                  Some(nextOwner)
                }

                entries(index) = ref.update(getOrInsertTypeName(parts.last), myOwner)
              }
            }
        }


      case _ => // Ignore
    }
  }

  private def getOrInsertTermName(name: String): Int = {
    termIndices.getOrElse(name, appendEntry(TermName(name)))
  }

  private def getOrInsertTypeName(name: String): Int = {
    typeIndices.getOrElse(name, appendEntry(TypeName(name)))
  }

  private def appendEntry(name: TableEntry): Int = {
    val index = entries.size
    entries += name
    name match {
      case termName: TermName =>
        termIndices.put(termName.name, index)
      case typeName: TypeName =>
        typeIndices.put(typeName.name, index)

      case _ => // NoOp
    }

    index
  }

  def resolveRef(extMod: Ref): Option[String] = {

    val myName = entries(extMod.nameRef) match {
      case term: Name => term.name
      case raw: RawEntry => throw new RuntimeException(s"Unexpected raw type for nameref ${raw.tag}")
      case other => throw new RuntimeException(s"Unexpected type for nameref $other")
    }
    extMod.ownerRef match {
      case None => Some(myName)
      case Some(owner) =>
        entries(owner) match {
          case name: TermName =>
            Some(s"$name/$myName")
          case mod: Ref =>
            resolveRef(mod).map(p => s"$p/$myName")
          case raw: RawEntry if raw.tag == PickleFormat.NONEsym =>
            None
          case raw: RawEntry =>
            println(s"Not a known owner type tag for $myName : ${raw.tag}")
            None
        }
    }
  }
}

object TableEntry {
  trait TableEntry {
    def write(pb: PickleBuffer): Unit
  }
  case class RawEntry(tag: Int, bytes: Array[Byte]) extends TableEntry {
    override def write(pb: PickleBuffer): Unit = {
      pb.writeNat(tag)
      pb.writeNat(bytes.length)
      bytes.foreach(b => pb.writeByte(b.toInt))
    }
  }

  abstract class Name(val tag: Int) extends TableEntry {
    val name: String

    override def write(pb: PickleBuffer): Unit = {
      val bytes = name.getBytes("UTF-8")

      pb.writeNat(tag)
      pb.writeNat(bytes.length)
      bytes.foreach(b => pb.writeByte(b.toInt))
    }
  }

  case class TermName(name: String) extends Name(PickleFormat.TERMname)
  case class TypeName(name: String) extends Name(PickleFormat.TYPEname)

  abstract class Ref(val tag: Int) extends TableEntry {
    val nameRef: Int
    val ownerRef: Option[Int]

    def update(newNameRef: Int, newOwnerRef: Option[Int]): Ref

    override def write(pb: PickleBuffer): Unit = {
      pb.writeNat(tag)
      ownerRef match {
        case Some(owner) =>
          pb.writeNat(sizeNat(nameRef) + sizeNat(owner))
          pb.writeNat(nameRef)
          pb.writeNat(owner)
        case None =>
          pb.writeNat(sizeNat(nameRef))
          pb.writeNat(nameRef)
      }
    }
  }

  case class ExtModClassRef(nameRef: Int, ownerRef: Option[Int]) extends Ref(PickleFormat.EXTMODCLASSref) {
    def update(newNameRef: Int, newOwnerRef: Option[Int]): Ref = ExtModClassRef(newNameRef, newOwnerRef)
  }
  case class ExtRef(nameRef: Int, ownerRef: Option[Int]) extends Ref(PickleFormat.EXTref) {
    def update(newNameRef: Int, newOwnerRef: Option[Int]): Ref = ExtRef(newNameRef, newOwnerRef)
  }

  def apply(tag: Int, bytes: Array[Byte]): TableEntry = {
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

  def sizeNat(value: Int): Int = {
    var count = 0
    var v = value
    do {
      v = v >> 7
      count += 1
    } while (v != 0)
    count
  }

  // Utility class to read the content of a single table entry
  class ByteArrayReader(bytes: Array[Byte]) {
    private var readIndex = 0

    /** Read a byte */
    def readByte(): Int = {
      val x = bytes(readIndex).toInt
      readIndex += 1
      x
    }

    /** Read a natural number in big endian format, base 128.
     * All but the last digits have bit 0x80 set. */
    def readNat(): Int = readLongNat().toInt

    def readLongNat(): Long = {
      var b = 0L
      var x = 0L
      do {
        b = readByte().toLong
        x = (x << 7) + (b & 0x7f)
      } while ((b & 0x80) != 0L)
      x
    }

    /** Read a long number in signed big endian format, base 256. */
    def readLong(len: Int): Long = {
      var x = 0L
      var i = 0
      while (i < len) {
        x = (x << 8) + (readByte() & 0xff)
        i += 1
      }
      val leading = 64 - (len << 3)
      x << leading >> leading
    }

    def atEnd: Boolean = readIndex == bytes.length
  }
}