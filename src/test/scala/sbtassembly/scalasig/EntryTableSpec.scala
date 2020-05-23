package sbtassembly.scalasig

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.reflect.ScalaSignature
import scala.reflect.internal.pickling.ByteCodecs

class Test

abstract class Test2 {
  val test: Test
}

class EntryTableSpec extends AnyWordSpec with Matchers with OptionValues {

  "entry table" should {
    "parse annotation bytes" in {
      val encoded = classOf[Test2].getAnnotation(classOf[ScalaSignature])
      val bytes = encoded.bytes().getBytes("UTF-8")

      val len = ByteCodecs.decode(bytes)
      val table = EntryTable.fromBytes(bytes.slice(0, len))

      resolveName(table, "Test") shouldBe Some("sbtassembly.scalasig.Test")
    }

    "rename package" in {
      val encoded = classOf[Test2].getAnnotation(classOf[ScalaSignature])
      val bytes = encoded.bytes().getBytes("UTF-8")

      val len = ByteCodecs.decode(bytes)
      val table = EntryTable.fromBytes(bytes.slice(0, len))

      table.renameEntries {
        case pckg if pckg.startsWith("sbtassembly.scalasig") => Some("shaded.sbtassembly.scalasig")
        case _ => None
      }

      resolveName(table, "Test") shouldBe Some("shaded.sbtassembly.scalasig.Test")

      table.renameEntries {
        case pckg if pckg.startsWith("shaded.sbtassembly.scalasig") => Some("sbtassembly.scalasig.shadedtoo")
        case _ => None
      }

      resolveName(table, "Test") shouldBe Some("sbtassembly.scalasig.shadedtoo.Test")
    }

    "return same serialized bytes" in {
      val encoded = classOf[Test2].getAnnotation(classOf[ScalaSignature])
      val bytes = encoded.bytes().getBytes("UTF-8")

      val len = ByteCodecs.decode(bytes)
      val decoded = bytes.slice(0, len)
      val table = EntryTable.fromBytes(decoded)

      val serialized = table.toBytes

      serialized.length shouldBe decoded.length +- 1  // Scala compiler (sometimes) adds an extra zero. We don't.
      EntryTable.fromBytes(serialized).toSeq shouldBe table.toSeq
    }
  }

  private def resolveName(entryTable: EntryTable, name: String): Option[String] = {
    val entries = entryTable.toSeq.zipWithIndex
    def findNameIndex: Option[Int] = entries.collectFirst {
      case (e: NameEntry, index) if e.name == name => index
    }

    def findRefEntry(nameIndex: Int): Option[RefEntry] = entries.collectFirst {
      case (e: RefEntry, _) if e.nameRef == nameIndex => e
    }

    for {
      index <- findNameIndex
      ref <- findRefEntry(index)
      resolved <- entryTable.resolveRef(ref)
    } yield resolved
  }
}
