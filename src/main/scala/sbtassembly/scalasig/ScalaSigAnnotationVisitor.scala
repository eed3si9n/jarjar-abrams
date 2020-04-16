package sbtassembly.scalasig

import java.io.ByteArrayOutputStream

import org.objectweb.asm.{AnnotationVisitor, ClassVisitor, Opcodes}

import scala.reflect.internal.pickling.ByteCodecs

class ScalaSigClassVisitor(cv: ClassVisitor, renamer: String => Option[String]) extends ClassVisitor(Opcodes.ASM7, cv) {

  override def visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = {
    if (descriptor == "Lscala/reflect/ScalaSignature;" || descriptor == "Lscala/reflect/ScalaLongSignature;") {
      new ScalaSigAnnotationVisitor(visible, cv, renamer)
    } else {
      super.visitAnnotation(descriptor, visible)
    }
  }
}

class ScalaSigAnnotationVisitor(
  visible: Boolean,
  cv: ClassVisitor,
  renamer: String => Option[String]
) extends AnnotationVisitor(Opcodes.ASM7) {

  private val MaxStringSizeInBytes = 65535
  private val annotationBytes: ByteArrayOutputStream = new ByteArrayOutputStream()

  override def visit(name: String, value: Any): Unit = {
    // Append all the annotation bytes, whether is is a long or normal signature
    val bytes = value.asInstanceOf[String].getBytes("UTF-8")
    annotationBytes.write(bytes)
  }

  override def visitArray(name: String): AnnotationVisitor = {
    // Array values are handled by this same visitor
    this
  }

  override def visitEnd(): Unit = {
    val encoded = annotationBytes.toByteArray
    val len = ByteCodecs.decode(encoded)

    val table = EntryTable.fromBytes(encoded.slice(0, len))
    table.renameEntries(renamer)

    val chars = ubytesToCharArray(mapToNextModSevenBits(ByteCodecs.encode8to7(table.toBytes)))
    val utf8EncodedLength = chars.foldLeft(0) { (count, next) => if (next == 0) count + 2 else count + 1}

    if (utf8EncodedLength > MaxStringSizeInBytes) {
      // Encode as ScalaLongSignature containing an array of strings
      val av = cv.visitAnnotation("Lscala/reflect/ScalaLongSignature;", visible)

      def nextChunk(from: Int): Array[Char] = {
        if (from == chars.length) {
          Array.empty
        } else {
          var size = 0
          var index = 0

          while(size < MaxStringSizeInBytes && from + index < chars.length) {
            val c = chars(from + index)
            size += (if (c == 0) 2 else 1)
            index += 1
          }

          chars.slice(from, from + index)
        }
      }

      // Write the array of strings as chunks of max MaxStringSizeInBytes bytes
      val arrayVisitor = av.visitArray("bytes")

      var offset = 0
      var chunk = nextChunk(offset)
      while(chunk.nonEmpty) {
        arrayVisitor.visit("bytes", new String(chunk))
        offset += chunk.length
        chunk = nextChunk(offset)
      }
      arrayVisitor.visitEnd()

      av.visitEnd()
    } else {
      // Encode as ScalaSignature containing a single string
      val av = cv.visitAnnotation("Lscala/reflect/ScalaSignature;", visible)
      av.visit("bytes", new String(chars))
      av.visitEnd()
    }
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
}