package com.eed3si9n.jarjar.util;

/**
 * Raised when a duplicate entry is found in a Jar file being processed.
 */
public class DuplicateJarEntryException extends RuntimeException {

  public DuplicateJarEntryException(String jarFile, String name) {
    super("Duplicate jar entry: " + name + " (in " + jarFile + ")");
  }

}
