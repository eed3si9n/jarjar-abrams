package org.pantsbuild.jarjar.misplaced;

/**
 * Factory singleton for creating a MisplacedProcessorFactory from a user-level strategy string.
 */
public class MisplacedClassProcessorFactory {

  public enum Strategy {
    /** Strategy which fails-fast with an exception upon hitting misplaced class. */
    FATAL,

    /** Strategy which skips past misplaced classes, excluding them from shading. */
    SKIP,

    /** Strategy which omits misplaced classes from the output. */
    OMIT,

    /**
     * Strategy which renames misplaced classes to their "proper" location based on their
     * fully-qualified class name.
     */
    MOVE,
  };

  private static MisplacedClassProcessorFactory me;

  public static synchronized MisplacedClassProcessorFactory getInstance() {
    if (me == null) {
      me = new MisplacedClassProcessorFactory();
    }
    return me;
  }

  private MisplacedClassProcessorFactory() {}

  /**
   * Returns the default misplaced class processor, which is "omit".
   */
  public MisplacedClassProcessor getDefaultProcessor() {
    return new OmitMisplacedClassProcessor();
  }

  /**
   * Creates a MisplacedClassProcessor according for the given strategy name.
   *
   * @param name The case-insensitive user-level strategy name (see the STRATEGY_* constants).
   * @return The MisplacedClassProcessor corresponding to the strategy name, or the result of
   * getDefaultProcessor() if name is null.
   * @throws IllegalArgumentException if an unrecognized non-null strategy name is specified.
   */
  public MisplacedClassProcessor getProcessorForName(String name) {
    if (name == null) {
      return getDefaultProcessor();
    }

    switch (Strategy.valueOf(name.toUpperCase())) {
      case FATAL: return new FatalMisplacedClassProcessor();
      case MOVE: return new MoveMisplacedClassProcessor();
      case OMIT: return new OmitMisplacedClassProcessor();
      case SKIP: return new SkipMisplacedClassProcessor();
    }

    throw new IllegalArgumentException("Unrecognized strategy name \"" + name + "\".");
  }

}
