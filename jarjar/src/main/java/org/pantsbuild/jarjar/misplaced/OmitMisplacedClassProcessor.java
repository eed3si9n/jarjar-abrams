package org.pantsbuild.jarjar.misplaced;

import org.pantsbuild.jarjar.util.EntryStruct;

/**
 * Leave misplaced classes out of the jar.
 */
public class OmitMisplacedClassProcessor extends MisplacedClassProcessor {

  @Override public void handleMisplacedClass(EntryStruct classStruct, String className) {
    System.err.println("Omitting " + classStruct.name + ".");
  }

  @Override public boolean shouldTransform() {
    return false;
  }

  @Override public boolean shouldKeep() {
    return false;
  }
}
