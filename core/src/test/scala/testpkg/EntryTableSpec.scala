package testpkg

import verify._
import scala.reflect.ScalaSignature
import scala.reflect.internal.pickling.ByteCodecs
import com.eed3si9n.jarjarabrams.scalasig._

class Test

abstract class Test2 {
  val test: Test
}

object EntryTableSpec extends BasicTestSuite {
  test("entry table should parse annotation bytes") {
    val encoded = classOf[Test2].getAnnotation(classOf[ScalaSignature])
    val bytes = encoded.bytes().getBytes("UTF-8")

    val len = ByteCodecs.decode(bytes)
    val table = EntryTable.fromBytes(bytes.slice(0, len))
    assert(resolveName(table, "Test") == Some("testpkg.Test"))
  }

  test("entry table should rename package") {
    val encoded = classOf[Test2].getAnnotation(classOf[ScalaSignature])
    val bytes = encoded.bytes().getBytes("UTF-8")

    val len = ByteCodecs.decode(bytes)
    val table = EntryTable.fromBytes(bytes.slice(0, len))

    table.renameEntries {
      case pckg if pckg.startsWith("testpkg") => Some("shaded.testpkg")
      case _                                  => None
    }

    assert(resolveName(table, "Test") == Some("shaded.testpkg.Test"))

    table.renameEntries {
      case pckg if pckg.startsWith("shaded.testpkg") =>
        Some("testpkg.shadedtoo")
      case _ => None
    }

    assert(resolveName(table, "Test") == Some("testpkg.shadedtoo.Test"))
  }

  test("entry table should return same serialized bytes") {
    val encoded = classOf[Test2].getAnnotation(classOf[ScalaSignature])
    val bytes = encoded.bytes().getBytes("UTF-8")

    val len = ByteCodecs.decode(bytes)
    val decoded = bytes.slice(0, len)
    val table = EntryTable.fromBytes(decoded)

    val serialized = table.toBytes

    assert(scala.math.abs(serialized.length - decoded.length) <= 1) // Scala compiler (sometimes) adds an extra zero. We don't.
    assert(EntryTable.fromBytes(serialized).toSeq == table.toSeq)
  }

  private def resolveName(entryTable: EntryTable, name: String): Option[String] = {
    import TaggedEntry._
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
