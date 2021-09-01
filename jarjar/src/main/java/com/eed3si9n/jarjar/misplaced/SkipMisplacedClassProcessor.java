package com.eed3si9n.jarjar.misplaced;

import com.eed3si9n.jarjar.util.EntryStruct;

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
