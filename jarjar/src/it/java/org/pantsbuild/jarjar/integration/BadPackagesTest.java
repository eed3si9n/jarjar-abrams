package org.pantsbuild.jarjar.integration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junit.framework.AssertionFailedError;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * These tests exercise JarJar's behavior when it processes classes whose jar entries do not match
 * their fully-qualified class names (ie when classes are in the "wrong" directory).
 */
public class BadPackagesTest extends IntegrationTestBase {

  @Test
  public void testSkipMisnamedClass() throws Exception {
    File jar = createJarWithMisnamedClass();

    jar = shadeJar(jar, new HashMap<String, String>() {{
      put("verbose", "true");
      put("misplacedClassStrategy", "skip");
    }}, null);

    List<String> entries = getJarEntries(jar);
    Assert.assertTrue(entries.contains("Misnamed.class"));
    assertFalse(entries.contains("org/pantsbuild.jarjar/fake/Foobar.class"));
    assertTrue(entries.contains("README.md"));
  }

  @Test
  public void testSkipOnlyMisnamedClass() throws Exception {
    Map<String, String> classes = new HashMap<String, String>();
    classes.put("a.b.c.Foobar", "a.b.c.Foobar");
    classes.put("a.b.d.Foobar", "a.b.d.BadName");
    classes.put("a.b.e.Foobar", "a.b.e.Foobar");
    classes.put("a.c.d.Example", "a.c.d.Example");
    File jar = createJarWithClasses(classes);

    jar = shadeJar(jar, new HashMap<String, String>() {{
      put("verbose", "true");
      put("misplacedClassStrategy", "skip");
    }}, new String[] {
        "rule a.** b.@1",
    });

    List<String> entries = getJarEntries(jar);
    Assert.assertTrue(entries.contains("b/b/c/Foobar.class"));
    Assert.assertTrue(entries.contains("a/b/d/BadName.class"));
    // Make sure we correctly shade entries that come after the misnamed class.
    Assert.assertTrue(entries.contains("b/b/e/Foobar.class"));
    Assert.assertTrue(entries.contains("b/c/d/Example.class"));
  }

  @Test
  public void testOmitOnlyMisnamedClass() throws Exception {
    Map<String, String> classes = new HashMap<String, String>();
    classes.put("a.b.c.Foobar", "a.b.c.Foobar");
    classes.put("a.b.d.Foobar", "a.b.d.BadName");
    classes.put("a.b.e.Foobar", "a.b.e.Foobar");
    classes.put("a.c.d.Example", "a.c.d.Example");
    File jar = createJarWithClasses(classes);

    jar = shadeJar(jar, new HashMap<String, String>() {{
      put("verbose", "true");
      put("misplacedClassStrategy", "omit");
    }}, new String[] {
        "rule a.** b.@1",
    });

    List<String> entries = getJarEntries(jar);
    Assert.assertTrue(entries.contains("b/b/c/Foobar.class"));
    Assert.assertFalse(entries.contains("a/b/d/BadName.class"));
    Assert.assertFalse(entries.contains("b/b/d/BadName.class"));
    // Make sure we correctly shade entries that come after the misnamed class.
    Assert.assertTrue(entries.contains("b/b/e/Foobar.class"));
    Assert.assertTrue(entries.contains("b/c/d/Example.class"));
  }

  @Test
  public void testMoveMisnamedClass() throws Exception {
    File jar = createJarWithMisnamedClass();

    jar = shadeJar(jar, new HashMap<String, String>() {{
      put("verbose", "true");
      put("misplacedClassStrategy", "move");
    }}, null);

    List<String> entries = getJarEntries(jar);
    assertFalse(entries.contains("Misnamed.class"));
    Assert.assertTrue(entries.contains("org/pantsbuild/jarjar/fake/Foobar.class"));
    assertTrue(entries.contains("README.md"));
  }

  @Test
  public void testOmitMisnamedClass() throws Exception {
    File jar = createJarWithMisnamedClass();

    jar = shadeJar(jar, new HashMap<String, String>() {{
      put("verbose", "true");
      put("misplacedClassStrategy", "omit");
    }}, null);

    List<String> entries = getJarEntries(jar);
    assertFalse(entries.contains("Misnamed.class"));
    Assert.assertFalse(entries.contains("org/pantsbuild/jarjar/fake/Foobar.class"));
    assertTrue(entries.contains("README.md"));
  }

  @Test
  public void testFatalMisnamedClass() throws Exception {
    File jar = createJarWithMisnamedClass();

    boolean failed = false;
    try {
      jar = shadeJar(jar, new HashMap<String, String>() {{
        put("verbose", "true");
        put("misplacedClassStrategy", "fatal");
      }}, null);
    } catch (JarJarFailedException e) {
      failed = true;
      Assert.assertTrue(e.getMessage().contains("Fully-qualified classname does not match jar entry"));
    }
    Assert.assertTrue("Jar jar should have failed, but didn't!", failed);

    List<String> entries = getJarEntries(jar);
    Assert.assertTrue(entries.contains("Misnamed.class"));
    assertFalse(entries.contains("org/pantsbuild.jarjar/fake/Foobar.class"));
    assertTrue(entries.contains("README.md"));
  }

  private File createJarWithMisnamedClass() throws Exception {
    Map<String, String> classes = new HashMap<String, String>();
    classes.put("org.pantsbuild.jarjar.fake.Foobar", "Misnamed");
    return createJarWithClasses(classes);
  }

  private File createJarWithClasses(Map<String, String> classNameMap) throws Exception {
    Map<String, String> files = new HashMap<String, String>();
    Map<String, String> nameToBinaryPath = new HashMap<String, String>();
    List<String> sourcePaths = new ArrayList<String>(classNameMap.size());
    files.put("README.md", "# Just making sure that normal resource files still work fine.");
    for (String className : classNameMap.keySet()) {
      String basePath = className.replaceAll("[.]", "/");
      String sourcePath = basePath + ".java";
      String binaryPath = basePath + ".class";
      files.put(sourcePath, basicJavaFile(className));
      nameToBinaryPath.put(className, binaryPath);
      sourcePaths.add(sourcePath);
    }

    String[] paths = sourcePaths.toArray(new String[sourcePaths.size()]);

    File folder = createTree(files);
    Assert.assertTrue(tryCompile(folder, paths, "-source", "6", "-target", "6"));

    for (String className : classNameMap.keySet()) {
      String dstName = classNameMap.get(className).replaceAll("[.]", "/") + ".class";
      File srcFile = new File(folder + File.separator + nameToBinaryPath.get(className));
      File dstFile = new File(folder + File.separator + dstName);
      if (!srcFile.getPath().equals(dstFile.getPath())) {
        dstFile.getParentFile().mkdirs();
        srcFile.renameTo(dstFile);
      }
    }

    for (String file : new FileTree(folder)) {
      if (file.endsWith(".java")) {
        new File(folder.getAbsolutePath() + File.separator + file).delete();
      }
    }

    return createJar(folder);
  }

  /**
   * Creates a jar file with a single class, compiled in java 6.
   *
   * A duplicate version of the class is created in a "oldversion" directory, and included
   * in the same jar.
   *
   * This targets a bug where jarjar would move classes which are not in a folder according to their
   * package name BACK into the appropriate folder, which can potentially cause
   * DuplicateJarEntryExcetions, in the very specific case where two versions of a class exist in
   * the same jar, with one version stored in a separate directory.
   *
   * In general, this situation should never happen and is reflective of corrupted jars, but
   * unfortunately there are some examples of these jars in the wild
   * (eg http://mvnrepository.com/artifact/com.sun.xml.bind/jaxb-xjc/2.2). These can get pulled in
   * as transitive dependencies when folks use lots of opensource software without them even
   * knowing, causing jar shading to explode.
   *
   * @return
   * @throws IOException
   */
  private File createJarWithOldVersion() throws IOException {
    String className = "org.pantsbuild.jarjar.fake.Foobar";
    String basePath = className.replaceAll("[.]", "/");
    String sourcePath = basePath + ".java";
    String binaryPath = basePath + ".class";

    Map<String, String> files = new HashMap<String, String>();
    files.put(sourcePath, basicJavaFile(className));

    String[] paths = { sourcePath };

    File folder = createTree(files);
    Assert.assertTrue(tryCompile(folder, paths, "-source", "6", "-target", "6"));

    File srcFile = new File(folder + File.separator + binaryPath);
    File dstFile = new File(folder + File.separator + "oldversion" + File.separator + binaryPath);
    dstFile.getParentFile().mkdirs();
    srcFile.renameTo(dstFile);

    Assert.assertTrue(tryCompile(folder, paths, "-source", "6", "-target", "6"));

    for (String file : new FileTree(folder)) {
      if (file.endsWith(".java")) {
        new File(folder.getAbsolutePath() + File.separator + file).delete();
      }
    }

    return createJar(folder);
  }

  @Test
  public void testJarWithOldVersionsPackage() throws Exception {
    try {
      File jar = createJarWithOldVersion();
      List<String> entries = getJarEntries(jar);
      Assert.assertTrue("Unshaded jar does not have new version of Foobar.",
          entries.contains("org/pantsbuild/jarjar/fake/Foobar.class"));
      Assert.assertTrue("Unshaded jar does not have old version of Foobar.",
          entries.contains("oldversion/org/pantsbuild/jarjar/fake/Foobar.class"));

      File shadedJar = shadeJar(jar, new HashMap<String, String>() {{
        put("verbose", "true");
        put("misplacedClassStrategy", "skip");
      }}, null);
      entries = getJarEntries(shadedJar);
      Assert.assertTrue("Shaded jar does not have new version of Foobar.",
          entries.contains("org/pantsbuild/jarjar/fake/Foobar.class"));
      Assert.assertTrue("Shaded jar does not have old version of Foobar.",
          entries.contains("oldversion/org/pantsbuild/jarjar/fake/Foobar.class"));
    } catch (AssertionFailedError e) {
      Assert.fail(e.getMessage());
    }
  }

}
