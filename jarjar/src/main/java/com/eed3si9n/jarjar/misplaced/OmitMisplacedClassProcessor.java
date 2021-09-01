package com.eed3si9n.jarjar.misplaced;

import com.eed3si9n.jarjar.util.EntryStruct;

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
