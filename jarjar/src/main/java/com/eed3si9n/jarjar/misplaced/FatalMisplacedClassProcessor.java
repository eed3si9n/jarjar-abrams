package com.eed3si9n.jarjar.misplaced;

import com.eed3si9n.jarjar.util.EntryStruct;

/**
 * Fails-fast with an exception upon finding a misplaced class.
 */
public class FatalMisplacedClassProcessor extends MisplacedClassProcessor {

  @Override public void handleMisplacedClass(EntryStruct classStruct, String className) {
    throw new FatalMisplacedClassException(formatMisplacedClassMessage(classStruct, className));
  }

  @Override public boolean shouldTransform() {
    return false;
  }

  @Override public boolean shouldKeep() {
    return false;
  }

  @Override public void handleUnreadableClass(EntryStruct classStruct, Exception cause) {
    throw new FatalMisplacedClassException(
        "Unable to read classname from bytecode in " + classStruct.name
            + ", so shading it is impossible ("
            + cause.getClass().getName() + ": " + cause.getMessage() + ")",
        cause);
  }

  public static class FatalMisplacedClassException extends RuntimeException {
    FatalMisplacedClassException(String message) {
      super(message);
    }

    FatalMisplacedClassException(String message, Throwable cause) {
      super(message, cause);
    }
  }

}
