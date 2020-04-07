package org.pantsbuild.jarjar

import org.objectweb.asm.{AnnotationVisitor, ClassVisitor, Opcodes}

import scala.reflect.internal.pickling.ByteCodecs

class ScalaSigClassVisitor(fileName: String, cv: ClassVisitor, rules: Seq[Rule]) extends ClassVisitor(Opcodes.ASM7, cv) {

  override def visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = {
    if (descriptor == "Lscala/reflect/ScalaSignature;") {
      new ScalaSigAnnotationVisitor(fileName, super.visitAnnotation(descriptor, visible), rules)
    } else if (descriptor == "Lscala/reflect/ScalaLongSignature;") {
      throw new RuntimeException("Shading ScalaLongSignature not implemented")
    } else {
      super.visitAnnotation(descriptor, visible)
    }
  }
}

class ScalaSigAnnotationVisitor(fileName: String, av: AnnotationVisitor, rules: Seq[Rule]) extends AnnotationVisitor(Opcodes.ASM7, av) {

  override def visit(name: String, value: Any): Unit = {

    val bytes = value.asInstanceOf[String].getBytes("UTF-8")
    val len = ByteCodecs.decode(bytes)

    val table = EntryTable.fromBytes(bytes.slice(0, len))
    table.renameEntries(rules)

    val newBytes = table.toBytes
    val newValue = new String(ubytesToCharArray(mapToNextModSevenBits(scala.reflect.internal.pickling.ByteCodecs.encode8to7(newBytes))))

    super.visit(name, newValue)
  }

  // These encoding functions are copied from scala.reflect.internal.AnnotationInfos.ScalaSigBytes
  private def mapToNextModSevenBits(src: Array[Byte]): Array[Byte] = {
    var i = 0
    val srclen = src.length
    while (i < srclen) {
      val in = src(i)
      src(i) = if (in == 0x7f) 0.toByte else (in + 1).toByte
      i += 1
    }
    src
  }

  def ubytesToCharArray(bytes: Array[Byte]): Array[Char] = {
    val ca = new Array[Char](bytes.length)
    var idx = 0
    while(idx < bytes.length) {
      val b: Byte = bytes(idx)
      assert((b & ~0x7f) == 0)
      ca(idx) = b.asInstanceOf[Char]
      idx += 1
    }
    ca
  }

  override def visitAnnotation(name: String, descriptor: String): AnnotationVisitor = {
    super.visitAnnotation(name, descriptor)
  }

  override def visitArray(name: String): AnnotationVisitor = {
    super.visitArray(name)
  }

  override def visitEnd(): Unit = {
    super.visitEnd()
  }
}