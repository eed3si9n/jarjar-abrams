package org.pantsbuild.jarjar.integration;


import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class JavaVersionsTest extends IntegrationTestBase {

  @Test
  public void testShadeJava8Class() throws Exception {
    testVersion("1.8");
  }

  @Test
  public void testShadeJava7Class() throws Exception {
    testVersion("1.7");
  }

  @Test
  public void testShadeJava6Class() throws Exception {
    testVersion("1.6");
  }

  private void testVersion(String version) throws Exception {
    if (!javaVersionIsAtLeast(version)) {
      System.out.println("Cannot test java " + version + " if this is being run in less than " + version + ".");
      return;
    }

    File jar = createJarWithSingleClass(version);
    List<String> entries = getJarEntries(jar);
    assertTrue("SingleClass didn't make it into unshaded jar.",
        entries.contains("org/pantsbuild/jarjar/SingleClass.class"));

    File shaded = shadeJar(jar, null, null);
    entries = getJarEntries(shaded);
    assertTrue("SingleClass didn't make it into shaded jar (with no rules).",
        entries.contains("org/pantsbuild/jarjar/SingleClass.class"));

    shaded = shadeJar(jar, null, new String[] {
        "rule org.pantsbuild.jarjar.SingleClass org.pantsbuild.jarjar.ShadedClass"
    });
    entries = getJarEntries(shaded);
    assertTrue("ShadedClass didn't make it into shaded jar (with rename rule).",
        entries.contains("org/pantsbuild/jarjar/ShadedClass.class"));

    // TOOD write a test that demonstrates that KeepProcessor fails.
    // I can trigger the ASM exception, but that gets caught and logged
    shaded = shadeJar(jar, null, new String[] {
         "rule org.pantsbuild.jarjar.SingleClass org.pantsbuild.jarjar.ShadedClass",
         "keep org.pantsbuild.jarjar.**"
    });
    entries = getJarEntries(shaded);

  }


  private File createJarWithSingleClass(String javaVersion) throws Exception {
    String className = "org.pantsbuild.jarjar.SingleClass";
    String basePath = className.replaceAll("[.]", "/");
    String sourcePath = basePath + ".java";

    Map<String, String> files = new HashMap<String, String>();
    files.put(sourcePath, basicJavaFile(className));

    String[] sources = {sourcePath};

    File folder = createTree(files);
    if (javaVersion != "1.6" && javaVersion != "1.7") {
      assertTrue(tryCompile(folder, sources, "-source", javaVersion, "-target", javaVersion, "-parameters"));
    } else {
      assertTrue(tryCompile(folder, sources, "-source", javaVersion, "-target", javaVersion));
    }

    for (String file : new FileTree(folder)) {
      if (file.endsWith(".java")) {
        new File(folder.getAbsolutePath() + File.separator + file).delete();
      }
    }

    return createJar(folder);
  }

}
