package org.pantsbuild.jarjar.integration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import junit.framework.TestCase;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;


/**
 * Base class for jarjar integration tests. Provides common utilities.
 */
public abstract class IntegrationTestBase extends TestCase {

  @org.junit.Rule
  public TemporaryFolder workdir = new TemporaryFolder();

  private File jarjarBinary;

  protected File getJarJarBinary() throws IOException, InterruptedException {
    if (jarjarBinary != null) return jarjarBinary;
    File binary = new File("dist" + File.separator + "jarjar.jar");
    if (binary.exists()) {
      binary.delete();
    }
    Runtime.getRuntime().exec(new String[] {
        "./pants", "--no-lock", "binary", "src/main::",
    }).waitFor();
    jarjarBinary = binary;
    return binary;
  }

  /**
   * Invokes javac in the given working directory with the given arguments.
   * @param directory The working directory to run javac in.
   * @param args The varargs list of arguments to javac.
   * @return true if javac succeeds, false otherwise.
   */
  protected boolean tryCompile(File directory, String[] paths, String ... args) {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

    List<File> files = new ArrayList<File>(paths.length);
    for (String path : paths) {
      files.add(new File(directory.getAbsolutePath() + File.separator + path));
    }

    return compiler.getTask(null, fileManager, null, Arrays.asList(args), null,
        fileManager.getJavaFileObjectsFromFiles(files)).call();
  }

  /**
   * Returns the list of file entires in a jar file.
   * @param jarFile The jar file to list.
   * @return the List of entries.
   */
  protected List<String> getJarEntries(File jarFile) {
    String[] cmd = {"jar", "-tf", jarFile.getAbsolutePath()};
    try {
      List<String> lines = new LinkedList<String>();
      Process p = Runtime.getRuntime().exec(cmd);
      BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
      while (true) {
        try {
          String line = in.readLine();
          if (line == null) break;
          lines.add(line);
        } catch (IOException e) {
          break;
        }
      }

      p.waitFor();
      assertEquals(0, p.exitValue());
      return lines;
    } catch (IOException e) {
      return null;
    } catch (InterruptedException e) {
      return null;
    }
  }

  /**
   * Creates a new temporary folder with one file for each key in the map.
   *
   * The values for each key are written to the contents of each file.
   *
   * @param filenamesToContents A map of (relative path name) -> (contents of file).
   * @return the temporary directory file object.
   */
  protected File createTree(Map<String, String> filenamesToContents) throws IOException {
    File tempdir = workdir.newFolder();

    for (String filename : filenamesToContents.keySet()) {
      File file = new File(tempdir.getAbsolutePath() + File.separator + filename);
      if (!file.getParentFile().exists()) {
        file.getParentFile().mkdirs();
      }
      PrintStream out = new PrintStream(file);
      out.println(filenamesToContents.get(filename));
      out.close();
    }

    return tempdir;
  }

  /**
   * Creates a new jar file by zipping up the given folder.
   *
   * @param folder The folder to zip up.
   * @param manifestEntries Lines to write to the manifest file (optional).
   * @return The jar file.
   */
  protected File createJar(File folder, String ... manifestEntries) throws IOException {
    File outputJar = workdir.newFile();
    File manifestFile = workdir.newFile();

    PrintStream manifestOut = new PrintStream(manifestFile);
    for (String line : manifestEntries) {
      manifestOut.println(line);
    }

    List<String> jarargs = new LinkedList<String>();
    jarargs.add("jar");
    jarargs.add("-cfm");
    jarargs.add(outputJar.getAbsolutePath());
    jarargs.add(manifestFile.getAbsolutePath());

    List<String> filesToJar = new LinkedList<String>();
    for (String relativePath : new FileTree(folder)) {
      filesToJar.add(relativePath);
    }

    Collections.sort(filesToJar, new Comparator<String>() {
      @Override public int compare(String a, String b) {
        return a.compareTo(b);
      }
    });
    jarargs.addAll(filesToJar);

    String[] cmd = jarargs.toArray(new String[jarargs.size()]);

    Process p = Runtime.getRuntime().exec(cmd, null, folder);
    try {
      p.waitFor();
    } catch (InterruptedException e) {
    }

    assertEquals(0, p.exitValue());
    assertTrue(outputJar.exists());

    return outputJar;
  }

  /**
   * Invokes jarjar to shade the jarfile with the given rules.
   *
   * @param jarFile The jarFile to shade.
   * @param jarjarArgs Optional map of arguments for jarjar.
   * @param rules Optional list of shading rules for jarjar.
   * @return The output shaded jar.
   */
  protected File shadeJar(File jarFile, Map<String, String> jarjarArgs, String[] rules) throws Exception {
    File rulesFile = workdir.newFile();
    PrintStream out = new PrintStream(rulesFile);
    if (rules != null) {
      for (String rule : rules) {
        out.println(rule);
      }
    }
    out.close();

    File outFile = workdir.newFile();

    List<String> args = new ArrayList<String>();
    args.add("java");

    if (jarjarArgs != null) {
      for (String key : jarjarArgs.keySet()) {
        args.add("-D" + key + "=" + jarjarArgs.get(key));
      }
    }

    String[] typicalArgs = {
        "-cp", getJarJarBinary().getAbsolutePath(),
        "org.pantsbuild.jarjar.Main",
        "process",
        rulesFile.getAbsolutePath(),
        jarFile.getAbsolutePath(),
        outFile.getAbsolutePath()
    };
    for (String arg : typicalArgs) {
      args.add(arg);
    }

    Process p = Runtime.getRuntime().exec(args.toArray(new String[args.size()]));

    ProcessCommunicator pc = new ProcessCommunicator(p);
    int result = pc.communicate();
    if (result != 0) {
      throw new JarJarFailedException(pc);
    }
    return outFile;
  }

  /**
   * Generates the contents of a syntactically correct but useless java file.
   *
   * Just includes a package definition and an empty class body.
   *
   * @param fullyQualifiedName The fully qualified classname of the java class should use.
   */
  protected String basicJavaFile(String fullyQualifiedName) {
    fullyQualifiedName = fullyQualifiedName.replaceAll("[.]", File.separator);

    String packageName = null;
    String className = null;

    int slash = fullyQualifiedName.lastIndexOf('/');
    if (slash < 0) {
      className = fullyQualifiedName;
    } else {
      packageName = fullyQualifiedName.substring(0, slash).replaceAll(File.separator, ".");
      className = fullyQualifiedName.substring(slash+1);
    }

    StringBuffer sb = new StringBuffer(fullyQualifiedName.length()*2);
    if (packageName != null) {
      sb.append("package ");
      sb.append(packageName);
      sb.append(";\n\n");
    }
    sb.append("public class ");
    sb.append(className);
    sb.append(" { /* NOTHING TO SEE HERE */ }\n");

    return sb.toString();
  }

  /**
   * Returns true if the system java version is at least as great as the input java version.
   *
   * @param version The java version, formated as %d.%d (eg, 1.8).
   * @return
   */
  public boolean javaVersionIsAtLeast(String version) {
    String enumName = "JAVA_" + version.replace('.', '_');
    return SystemUtils.isJavaVersionAtLeast(JavaVersion.valueOf(enumName));
  }

}
