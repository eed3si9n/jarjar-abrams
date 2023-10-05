package testpkg

import verify._
import java.nio.file.{ Files, Path, Paths }
import com.eed3si9n.jarjarabrams.{ Main, Shader, Zip }

object ShaderTest extends BasicTestSuite {
  test("shade bytebuddy") {
    testShading(Paths.get("example/byte-buddy-agent.jar"), "foo/Attacher.class")
  }

  test("shade shapeless") {
    testShading(Paths.get("example/shapeless_2.12-2.3.2.jar"), "bar/shapeless/Poly8.class")
  }

  def testShading(inJar: Path, expectedClass: String): Unit = {
    val tempJar = Files.createTempFile("test", ".jar")
    val rules = Paths.get("example/shade.rules")
    new Main().process(rules, inJar, tempJar)
    val tempDir = Files.createTempDirectory("jarjartest")
    Zip.unzip(tempJar, tempDir)
    val entries = Shader.makeMappings(tempDir).map(_._2)
    assert(entries.contains(expectedClass))
  }
}
