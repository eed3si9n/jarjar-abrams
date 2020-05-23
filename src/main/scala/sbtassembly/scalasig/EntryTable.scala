package sbtassembly.scalasig

import java.io.ByteArrayOutputStream

import scala.collection.mutable
import scala.reflect.internal.pickling.PickleFormat

/**
 * Mutable table of tagged entries
 * @param majorVersion major table version
 * @param minorVersion minor table version
 * @param entries initial table entries
 */
class EntryTable(majorVersion: Int, minorVersion: Int, entries: mutable.Buffer[TaggedEntry]) {
  // Mapping of known TermName or TypeNames to their index in the table.
  private val nameIndices: mutable.Map[NameEntry, Int] = mutable.HashMap(
    entries.zipWithIndex.collect {
      case (entry: NameEntry, index) => (entry, index)
    }:_*
  )

  /**
   * Return the current table entries as an immutable seq.
   * @return table entries
   */
  def toSeq: Seq[TaggedEntry] = entries.toVector

  /**
   * Rename term and type entries in this table according to the renamer function.
   * A name or type is referred to by a Ref entry. The existing ref entries are reused to references to them will remain intact.
   * Unused entries will not be removed from the table.
   *
   * @param renamer renames a fully qualified type or term name or return None if it does not match.
   */
  def renameEntries(renamer: String => Option[String]): Unit = {

    entries.zipWithIndex.collect {
      case (ref: RefEntry, index) =>
        entries(ref.nameRef) match {
          case nameEntry: NameEntry =>
            for {
              fqName <- resolveRef(ref)
              renamed <- renamer(fqName)
            } {
              val parts = renamed.split('.')

              val myOwner = parts.init.foldLeft(Option.empty[Int]) { (owner, part) =>
                val nameIndex = getOrAppendNameEntry(NameEntry(PickleFormat.TERMname, part))
                val nextOwner = appendEntry(RefEntry(PickleFormat.EXTMODCLASSref, nameIndex, owner))
                Some(nextOwner)
              }

              entries(index) = ref.copy(nameRef = getOrAppendNameEntry(nameEntry.copy(name = parts.last)), ownerRef = myOwner)
            }

          case other =>
            throw new RuntimeException(s"Ref entry does not point to a name but to a ${other.tag}")
        }
    }
  }

  // Return existing name entry or append a new one.
  private def getOrAppendNameEntry(name: NameEntry): Int = {
    nameIndices.getOrElse(name, appendEntry(name))
  }

  private def appendEntry(entry: TaggedEntry): Int = {
    val index = entries.size
    entries += entry

    entry match {
      case name: NameEntry =>
        nameIndices.put(name, index)
      case _ => // NoOp
    }

    index
  }

  // Resolves a ref into a fully qualified name
  def resolveRef(extMod: RefEntry): Option[String] = {

    val myName = entries(extMod.nameRef) match {
      case term: NameEntry => term.name
      case raw: RawEntry => throw new RuntimeException(s"Unexpected raw type for nameref ${raw.tag}")
      case other => throw new RuntimeException(s"Unexpected type for nameref $other")
    }
    extMod.ownerRef match {
      case None => Some(myName)
      case Some(owner) =>
        entries(owner) match {
          case name: NameEntry =>
            Some(s"$name/$myName")
          case mod: RefEntry =>
            resolveRef(mod).map(p => s"$p.$myName")
          case raw: RawEntry if raw.tag == PickleFormat.NONEsym =>
            None
          case raw: RawEntry =>
            throw new RuntimeException(s"Not a known owner type tag for $myName : ${raw.tag}")
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
