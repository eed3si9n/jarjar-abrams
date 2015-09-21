package org.pantsbuild.jarjar.misplaced;

import org.pantsbuild.jarjar.util.EntryStruct;

/**
 * Handles misplaced classes by excluding them from shading
 * (which leaves them at their original location).
 */
public class SkipMisplacedClassProcessor extends MisplacedClassProcessor {

  @Override public void handleMisplacedClass(EntryStruct classStruct, String className) {
    System.err.println("Skipping shading of " + classStruct.name);
  }

  @Override public boolean shouldTransform() {
    return false;
  }

  @Override public boolean shouldKeep() {
    return true;
  }
}
