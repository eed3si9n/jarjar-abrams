package org.pantsbuild.jarjar.integration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

/**
 * Slurps the input/output streams of a Process.
 */
public class ProcessCommunicator {

  private Process process;
  private List<String> stderr;
  private List<String> stdout;

  public ProcessCommunicator(Process p) {
    this.process = p;
    this.stderr = new LinkedList<String>();
    this.stdout = new LinkedList<String>();
  }

  /**
   * Waits for the process to finish, and returns its returncode.
   *
   * @return The integer return code (exitValue()) of the process.
   * @throws InterruptedException
   */
  public int communicate() throws InterruptedException {
    slurpStream(process.getInputStream(), stdout);
    slurpStream(process.getErrorStream(), stderr);
    process.waitFor();
    return process.exitValue();
  }

  /**
   * Gets the lines of standard error.
   * @return a list of error lines.
   */
  public List<String> getStderrLines() {
    return stderr;
  }

  /**
   * Gets the lines of standard output.
   * @return a list of error lines.
   */
  public List<String> getStdoutLines() {
    return stdout;
  }

  /**
   * Gets the standard output as a single string.
   * @return the stdout text.
   */
  public String getStdoutText() {
    return toBlob(stdout);
  }

  /**
   * Gets the standard error as a single string.
   * @return the stderr text.
   */
  public String getStderrText() {
    return toBlob(stderr);
  }

  private String toBlob(List<String> lines) {
    StringBuffer sb = new StringBuffer(10*lines.size());
    for (String s : lines) {
      if (sb.length() > 0) {
        sb.append("\n");
      }
      sb.append(s);
    }
    return sb.toString();
  }

  private void slurpStream(InputStream sin, final List<String> list) {
    final BufferedReader in = new BufferedReader(new InputStreamReader(sin));
    new Thread(new Runnable() {
      public void run() {
        while (true) {
          try {
            String line = in.readLine();
            if (line == null)
              break;
            list.add(line);
          } catch (IOException e) {
            break;
          }
        }
      }
    }).start();
  }

}
