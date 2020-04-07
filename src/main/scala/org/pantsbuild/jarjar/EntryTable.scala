package org.pantsbuild.jarjar

import java.io.ByteArrayOutputStream

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.reflect.internal.pickling.PickleFormat

class EntryTable(val majorVersion: Int, minorVersion: Int, var entries: mutable.Buffer[TaggedEntry]) {
  private val termIndices: mutable.Map[String, Int] = mutable.HashMap(
    entries.zipWithIndex.collect {
      case (entry: TermName, index) => (entry.name, index)
    }:_*
  )

  private val typeIndices: mutable.Map[String, Int] = mutable.HashMap(
    entries.zipWithIndex.collect {
      case (entry: TypeName, index) => (entry.name, index)
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

  private def appendEntry(name: TaggedEntry): Int = {
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

  /**
   * Serializes this entry table into a byte array.
   */
  def toBytes: Array[Byte] = {
    val os = new ByteArrayOutputStream()
    val writer = new Nat.Writer {
      override def writeByte(b: Int): Unit = os.write(b)
    }

    writer.writeNat(majorVersion)
    writer.writeNat(minorVersion)
    writer.writeNat(entries.size)

    entries.foreach { entry =>
      val payloadBytes = entry.toBytes
      writer.writeNat(entry.tag)            // Tag of entry
      writer.writeNat(payloadBytes.length)  // Size of payload
      os.write(payloadBytes)
    }

    os.toByteArray
  }
}

object EntryTable {

  /**
   * Parse bytes into a EntryTable
   */
  def fromBytes(bytes: Array[Byte]): EntryTable = {
    val reader = new ByteArrayReader(bytes)

    val majorVersion = reader.readNat()
    val minorVersion = reader.readNat()

    val result = new Array[TaggedEntry](reader.readNat())

    result.indices foreach { index =>
      val tag = reader.readNat()
      val len = reader.readNat()

      result(index) = TaggedEntry(tag, reader.readBytes(len))
    }

    new EntryTable(majorVersion, minorVersion, result.toBuffer)
  }
}
