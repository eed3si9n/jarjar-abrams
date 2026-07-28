package com.eed3si9n.jarjar

import org.objectweb.asm.{ ClassReader, ClassWriter }
import util.{ EntryStruct, JarProcessor }
import com.eed3si9n.jarjarabrams.scalasig.ScalaSigClassVisitor

class ScalaSigProcessor(renamer: String => Option[String]) extends JarProcessor {
  override def process(struct: EntryStruct): Boolean = {

    if (!struct.name.endsWith(".class") || struct.skipTransform) true
    else {
      val classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
      val reader = new ClassReader(struct.data)
      val visitor = new ScalaSigClassVisitor(classWriter, renamer)

      reader.accept(visitor, ClassReader.EXPAND_FRAMES)
      // Only take the re-emitted bytes when a Scala signature was actually rewritten. Going
      // through ASM rebuilds the constant pool from scratch, which reorders it and can widen
      // ldc into ldc_w; classes this processor has nothing to do with -- every Java class, and
      // every Scala class the rules leave alone -- would otherwise come out of shading
      // gratuitously different from their input, for no change in behaviour.
      if (visitor.hasChanges) struct.data = classWriter.toByteArray
      true
    }
  }
}
