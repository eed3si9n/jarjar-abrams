package org.pantsbuild.jarjar

import org.objectweb.asm.{ClassReader, ClassWriter}
import org.pantsbuild.jarjar.util.{EntryStruct, JarProcessor}

class ScalaSigProcessor(renamer: String => Option[String]) extends JarProcessor {
  override def process(struct: EntryStruct): Boolean = {

    if (!struct.name.endsWith(".class") || struct.skipTransform) true
    else {
      val classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
      val reader = new ClassReader(struct.data)

      reader.accept(new ScalaSigClassVisitor(struct.name, classWriter, renamer), ClassReader.EXPAND_FRAMES)
      struct.data = classWriter.toByteArray
      true
    }
  }
}
