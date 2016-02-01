package org.pantsbuild.jarjar.misplaced;

import java.io.IOException;
import org.objectweb.asm.ClassReader;
import org.pantsbuild.jarjar.util.EntryStruct;
import org.pantsbuild.jarjar.util.JarProcessor;

/**
 * Processor that handles classes whose fully-qualified names do not match their jar entry location.
 */
public abstract class MisplacedClassProcessor implements JarProcessor {

  /**
   * Handles a class that was found at a location that does not match its fully qualified classname.
   * @param classStruct The jar EntryStruct for the class.
   * @param className The actual fully-qualified classname read from the binary data.
   */
  public abstract void handleMisplacedClass(EntryStruct classStruct, String className);

  /**
   * Whether any JarTransformers transform and relocate the struct to its "proper" location based on
   * its package name, or just skip over it.
   * @return True if the struct should be transformed, false otherwise.
   */
  public abstract boolean shouldTransform();

  /**
   * Returns whether the entry should be included in the output.
   * @return true if it should be kept, false if it should be omitted.
   */
  public abstract boolean shouldKeep();

  @Override public boolean process(EntryStruct struct)
      throws IOException {
    if (!struct.name.endsWith(".class")) return true;

    String originalClassName;
    try {
      originalClassName = new ClassReader(struct.data).getClassName() + ".class";
    } catch (Exception e) {
      System.err.println("Unable to read classname from bytecode in " + struct.name);
      System.err.println("Shading is therefore impossible, so this entry will be skipped.");
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
      struct.skipTransform = true;
      return true;
    }
    if (!originalClassName.equals(struct.name)) {
      System.err.println(formatMisplacedClassMessage(struct, originalClassName));
      handleMisplacedClass(struct, originalClassName);
      if (!shouldTransform()) {
        struct.skipTransform = true;
      }
      return shouldKeep();
    }
    return true;
  }

  protected String formatMisplacedClassMessage(EntryStruct classStruct, String className) {
    return "Fully-qualified classname does not match jar entry:\n"
        + "  jar entry: " + classStruct.name + "\n"
        + "  class name: " + className;
  }

}
