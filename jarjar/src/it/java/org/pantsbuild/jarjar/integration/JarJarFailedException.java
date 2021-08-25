package org.pantsbuild.jarjar.integration;

public class JarJarFailedException extends RuntimeException {
  public JarJarFailedException(ProcessCommunicator pc) {
    super("JarJar did not exit successfully: " + pc.getStderrText());
  }
}
