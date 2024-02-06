package com.eed3si9n.jarjar

import buildinfo.BuildInfo.shaderTest
import java.io.{DataInputStream, InputStream}
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import verify._

object CalciteShaderTest extends BasicTestSuite {
  final val byteBuddyJar = "example/byte-buddy-agent.jar"
  final val shapelessJar = "example/shapeless_2.12-2.3.2.jar"
  final val expectedByteBuddyClass = "foo/Attacher.class"
  final val expectedShapelessClass = "bar/shapeless/Poly8.class"

  val calcitePath = shaderTest.find(_.getName.contains("calcite")).get
  // println(calcitePath)
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


  // test("shade calcite") {
  //   import com.eed3si9n.jarjarabrams.Shader
  //   import com.eed3si9n.jarjarabrams.ShadeRule
  //   val ruleList = Seq(
  //     ShadeRule.rename("com.google.guava.**" -> "new_guava.com.google.guava.@1").inAll
  //   )
  //   val bytecodeShader = Shader.bytecodeShader(
  //     ruleList,
  //     verbose = true,
  //     skipManifest = false,
  //   )
  //   val shadeResult = bytecodeShader(sqlFunctionsBytecode, problematicClassfile)
  //   println(shadeResult)
  // }

  test("is the problem asm ClassReader?") {

    import org.objectweb.asm.{
      ClassReader,
      ClassVisitor,
      ClassWriter, 
      Opcodes,
    }
    import org.objectweb.asm.commons.{
      ClassRemapper,
      Remapper
    }
    import com.eed3si9n.jarjar.util.{
      GetNameClassWriter
    }
    
    val remapper = new Remapper() {}
    val emptyClassVisitor = new EmptyClassVisitor
    
    val classRepmapper = new ClassRemapper(emptyClassVisitor, remapper) {
      def setTarget(target: ClassVisitor) {
        cv = target
      }
    }
    
    val reader = new ClassReader(sqlFunctionsBytecode)
    val writer = new GetNameClassWriter(ClassWriter.COMPUTE_MAXS)

    classRepmapper.setTarget(writer)
    reader.accept(classRepmapper, ClassReader.EXPAND_FRAMES)
    val outputBytes = writer.toByteArray()


    val reader2 = new ClassReader(outputBytes)
    val classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
    val visitor = new ClassVisitor(Opcodes.ASM9, classWriter) {}
    reader2.accept(visitor, ClassReader.EXPAND_FRAMES)
  }
}
