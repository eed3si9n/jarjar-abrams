package testpkg

import verify._
import java.nio.file.{ Files, Paths }
import com.eed3si9n.jarjarabrams.{ Main, Shader, Zip }

object ShaderTest extends BasicTestSuite {
  test("shade a file") {
    val tempJar = Files.createTempFile("test", ".jar")
    val rules = Paths.get("example/shade.rules")
    val inJar = Paths.get("example/byte-buddy-agent.jar")
    new Main().process(rules, inJar, tempJar)
    val tempDir = Files.createTempDirectory("jarjartest")
    Zip.unzip(tempJar, tempDir)
    val entries = Shader.makeMappings(tempDir).map(_._2)
    assert(entries.contains("foo/Attacher.class"))
  }
}
