package testpkg

import verify._
import java.nio.file.{ Files, Path, Paths }
import com.eed3si9n.jarjarabrams.{ ShadeRule, Shader, Zip }
import buildinfo.BuildInfo.shaderTest
import java.util.jar.JarFile
import java.util.zip.ZipEntry


object ShaderTest extends BasicTestSuite {
  final val byteBuddyJar = "example/byte-buddy-agent.jar"
  final val shapelessJar = "example/shapeless_2.12-2.3.2.jar"
  final val expectedByteBuddyClass = "foo/Attacher.class"
  final val expectedShapelessClass = "bar/shapeless/Poly8.class"

  val calcite = new JarFile(shaderTest.find(_.getName.contains("calcite")).get)

  test("shade bytebuddy") {
    testShading(
      Paths.get(byteBuddyJar),
      resetTimestamp = false,
      expectedClass = expectedByteBuddyClass,
      expectedSha = "673a5b1d7282ec68def6d6e6845c29d96142e4e3b39796484e122cd92f65edee"
    )
  }

  test("shade bytebuddy (resetTimestamp)") {
    testShading(
      Paths.get(byteBuddyJar),
      resetTimestamp = true,
      expectedClass = expectedByteBuddyClass,
      expectedSha = "33ceee11fb2b5e4d46ebe552025bc17bc4d9391974c55e07d63f9e85d2ec381a"
    )
  }

  test("shade shapeless") {
    testShading(
      Paths.get(shapelessJar),
      resetTimestamp = false,
      expectedClass = expectedShapelessClass,
      expectedSha = "b0675ab6b2171faad08de45ccbc4674df569e03b434745ebd9e7442cd7846796"
    )
  }

  test("shade shapeless (resetTimestamp)") {
    testShading(
      Paths.get(shapelessJar),
      resetTimestamp = true,
      expectedClass = expectedShapelessClass,
      expectedSha = "68ac892591bb30eb2ba5c0c2c3195e7529e15bacd221b8bb3d75b154f5a4ce76"
    )
  }

  test("shade calcite") {
    val problematicClassfile = "org/apache/calcite/runtime/SqlFunctions.class"
    val input = calcite.getInputStream(new ZipEntry(problematicClassfile))
    val shadeRules = Seq(
      ShadeRule.rename("com.google.guava.**" -> s"new_guava.com.google.guava.@1").inAll
    )
    val bytecodeShader = Shader.bytecodeShader(
      shadeRules,
      verbose = false,
      skipManifest = false,
    )
    val shadeResult = bytecodeShader(input.readAllBytes, problematicClassfile)
    println(shadeResult)
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
