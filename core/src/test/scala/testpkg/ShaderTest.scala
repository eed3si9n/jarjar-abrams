package testpkg

import verify._
import java.nio.file.{ Files, Path, Paths }
import com.eed3si9n.jarjarabrams.{ Shader, Zip }

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
