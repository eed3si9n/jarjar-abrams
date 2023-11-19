package testpkg

import buildinfo.BuildInfo.shaderTest
import com.eed3si9n.jarjarabrams.{ ShadeRule, Shader }
import java.io.{DataInputStream, InputStream}
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import org.objectweb.asm.{ClassWriter, ClassReader, ClassVisitor, Opcodes}
import verify._

object CalciteShaderTest extends BasicTestSuite {
  final val byteBuddyJar = "example/byte-buddy-agent.jar"
  final val shapelessJar = "example/shapeless_2.12-2.3.2.jar"
  final val expectedByteBuddyClass = "foo/Attacher.class"
  final val expectedShapelessClass = "bar/shapeless/Poly8.class"

  val calcitePath = shaderTest.find(_.getName.contains("calcite")).get
  println(calcitePath)
  val problematicClassfile = "org/apache/calcite/runtime/SqlFunctions.class"

  def sqlFunctionsBytecode = {
    val calciteJar = new JarFile(calcitePath)
    readAllBytes(calciteJar.getInputStream(new ZipEntry(problematicClassfile)))
  }

  def readAllBytes(inputStream: InputStream): Array[Byte] = {
    val bytes = Array.ofDim[Byte](inputStream.available())
    val dataInputStream = new DataInputStream(inputStream)
    dataInputStream.readFully(bytes)
    bytes
  }

  test("shade calcite") {
    val shadeRules = Seq(
      ShadeRule.rename("com.google.guava.**" -> s"new_guava.com.google.guava.@1").inAll
    )
    val bytecodeShader = Shader.bytecodeShader(
      shadeRules,
      verbose = false,
      skipManifest = false,
    )
    val shadeResult = bytecodeShader(sqlFunctionsBytecode, problematicClassfile)
    println(shadeResult)
  }

  test("is the problem asm ClassReader?") {
    val classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
    val reader = new ClassReader(sqlFunctionsBytecode)
    val visitor = new ClassVisitor(Opcodes.ASM9, classWriter) {

    }
    reader.accept(visitor, ClassReader.EXPAND_FRAMES)
  }
}
