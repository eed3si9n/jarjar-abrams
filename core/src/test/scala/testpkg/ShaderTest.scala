package testpkg

import verify._
import java.io.{ ByteArrayOutputStream, InputStream }
import java.nio.file.{ Files, Path, Paths }
import java.util.Arrays
import java.util.zip.ZipFile
import com.eed3si9n.jarjar.ScalaInlineInfoReader
import com.eed3si9n.jarjarabrams.{ ShadeRule, Shader, Zip }

object ShaderTest extends BasicTestSuite {
  final val byteBuddyJar = "example/byte-buddy-agent.jar"
  final val shapelessJar = "example/shapeless_2.12-2.3.2.jar"
  final val expectedByteBuddyClass = "foo/Attacher.class"
  final val expectedShapelessClass = "bar/shapeless/Poly8.class"

  test("shade bytebuddy") {
    testShading(
      Paths.get(byteBuddyJar),
      resetTimestamp = false,
      expectedClass = expectedByteBuddyClass,
      expectedSha = "f3f441dd5ab28577083ab4e5235382db76fb9b60b7117a712691f0e25ab2cea4"
    )
  }

  test("shade bytebuddy (resetTimestamp)") {
    testShading(
      Paths.get(byteBuddyJar),
      resetTimestamp = true,
      expectedClass = expectedByteBuddyClass,
      expectedSha = "debbc6e00c39c1ad50f238055696538d98faf59458d10845154c4fb87234eddc"
    )
  }

  test("shade shapeless") {
    testShading(
      Paths.get(shapelessJar),
      resetTimestamp = false,
      expectedClass = expectedShapelessClass,
      expectedSha = "46269cce256194357e1c4cb06437f1e11b71a2fa33cd85512cf0b35385b6808e"
    )
  }

  test("shade shapeless (resetTimestamp)") {
    testShading(
      Paths.get(shapelessJar),
      resetTimestamp = true,
      expectedClass = expectedShapelessClass,
      expectedSha = "63561e67bcd6b69f943e5e2b84c56c18197956c63031ea4c6578e48ec6abe92d"
    )
  }

  test("leave Java classes no rule matches byte-for-byte alone") {
    assertUnmatchedClassesUnchanged(
      Paths.get(byteBuddyJar),
      ShadeRule.rename("shapeless.**" -> "bar.shapeless.@1").inAll
    )
  }

  test("leave Scala classes no rule matches byte-for-byte alone") {
    assertUnmatchedClassesUnchanged(
      Paths.get(shapelessJar),
      ShadeRule.rename("net.bytebuddy.agent.**" -> "foo.@1").inAll
    )
  }

  /**
   * Shading a jar with rules that match none of it has to hand back the classes it was given.
   * Reading a class with ASM and writing it back rebuilds the constant pool, which reorders it and
   * can widen ldc into ldc_w -- a difference in the output for no difference in behaviour.
   */
  def assertUnmatchedClassesUnchanged(inJar: Path, rules: ShadeRule*): Unit = {
    val tempJar = Files.createTempFile("test", ".jar")
    Shader.shadeFile(
      rules.toList,
      inJar,
      tempJar,
      verbose = false,
      skipManifest = false,
      resetTimestamp = false,
      warnOnDuplicateClass = false
    )
    val before = classEntries(inJar)
    val after = classEntries(tempJar)
    assert(before.keySet == after.keySet)
    val rewritten = before.collect {
      case (name, bytes) if !Arrays.equals(bytes, after(name)) => name
    }
    assert(rewritten.isEmpty)
  }

  /**
   * Scala 2.12 and 2.13 name the methods of a ScalaInlineInfo attribute by constant-pool index. A
   * processor that rewrites a class rebuilds its pool, so it has to re-emit those references or
   * they dangle, and the inliner reading the shaded class reports "Error while reading
   * InlineInfoAttribute ... Index N out of bounds for length N" (scalameta/scalameta#3338).
   */
  test("keep the inline info of a class a rule rewrites") {
    val tempJar = Files.createTempFile("test", ".jar")
    Shader.shadeFile(
      List(ShadeRule.rename("shapeless.**" -> "shaded.shapeless.@1").inAll),
      Paths.get(shapelessJar),
      tempJar,
      verbose = false,
      skipManifest = false,
      resetTimestamp = false,
      warnOnDuplicateClass = false
    )
    val before =
      ScalaInlineInfoReader.methods(classEntries(Paths.get(shapelessJar))(hlistOps))
    val after =
      ScalaInlineInfoReader.methods(classEntries(tempJar)("shaded/" + hlistOps))
    assert(before.size == 118)
    assert(after == before)
  }

  final val hlistOps = "shapeless/syntax/HListOps.class"

  test("keep unshadeable entries by default") {
    val shader = Shader.bytecodeShader(shadeRules, verbose = false, skipManifest = false)
    assert(shader(unreadableClass, "payload/Unreadable.class").isDefined)
    assert(shader(sampleClass, "payload/Misplaced.class").isEmpty)
  }

  test("fail on unshadeable entries under the fatal strategy") {
    val shader =
      Shader.bytecodeShader(shadeRules, verbose = false, skipManifest = false, Some("fatal"))
    intercept[RuntimeException] {
      shader(unreadableClass, "payload/Unreadable.class")
    }
    intercept[RuntimeException] {
      shader(sampleClass, "payload/Misplaced.class")
    }
  }

  lazy val shadeRules = List(ShadeRule.rename("payload.**" -> "shadedpayload.@1").inAll)

  lazy val sampleClass = classEntries(Paths.get(byteBuddyJar)).head._2

  lazy val unreadableClass = {
    val bytes = sampleClass.clone()
    bytes(6) = 0.toByte
    bytes(7) = 99.toByte
    bytes
  }

  def classEntries(jar: Path): Map[String, Array[Byte]] = {
    val zip = new ZipFile(jar.toFile)
    try {
      val builder = Map.newBuilder[String, Array[Byte]]
      val entries = zip.entries()
      while (entries.hasMoreElements) {
        val entry = entries.nextElement()
        if (entry.getName.endsWith(".class")) {
          val in = zip.getInputStream(entry)
          try builder += entry.getName -> readAll(in)
          finally in.close()
        }
      }
      builder.result()
    } finally zip.close()
  }

  def readAll(in: InputStream): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    val buffer = new Array[Byte](8192)
    var read = in.read(buffer)
    while (read >= 0) {
      out.write(buffer, 0, read)
      read = in.read(buffer)
    }
    out.toByteArray
  }

  def testShading(
      inJar: Path,
      resetTimestamp: Boolean,
      expectedClass: String,
      expectedSha: String
  ): Unit = {
    val tempJar = Files.createTempFile("test", ".jar")
    val rules = Shader.parseRulesFile(Paths.get("example/shade.rules"))
    Shader.shadeFile(
      rules,
      inJar,
      tempJar,
      verbose = false,
      skipManifest = false,
      resetTimestamp,
      warnOnDuplicateClass = false
    )
    val entries = Zip.list(tempJar).map(_._1)
    assert(entries.contains(expectedClass))
    val actualSha = Zip.sha256(tempJar)
    assert(actualSha == expectedSha)
  }
}
