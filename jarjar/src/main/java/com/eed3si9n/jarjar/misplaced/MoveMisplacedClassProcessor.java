package com.eed3si9n.jarjar.misplaced;

import com.eed3si9n.jarjar.util.EntryStruct;

/**
 * Moves misplaced classes to their proper location based on their package name.
 */
public class MoveMisplacedClassProcessor extends MisplacedClassProcessor {

  @Override public void handleMisplacedClass(EntryStruct classStruct, String className) {
    System.err.println("Renaming " + classStruct.name + " to " + className);
  }

  @Override public boolean shouldTransform() {
    return true;
  }

  @Override public boolean shouldKeep() {
    return true;
  }

}
