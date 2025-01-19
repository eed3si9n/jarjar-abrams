package com.eed3si9n.jarjar.util;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class IOUtilTest {
    @Test
    public void testCopyZipWithoutEmptyDirectories() throws IOException {
        // create a temporary directory tree with a few empty files and empty directories
        Path tree = Files.createTempDirectory("tree");
        Path zips = Files.createTempDirectory("zips");

        // Create a zip with some empty directories
        Path a = Files.createDirectory(Paths.get(tree.toString(), "a"));
        Files.createFile(Paths.get(a.toString(), "a.txt"));
        Files.createDirectory(Paths.get(tree.toString(), "b"));
        Path c = Files.createDirectory(Paths.get(tree.toString(), "c"));
        Files.createDirectory(Paths.get(c.toString(), "d"));
        File inputZipFile = Paths.get(zips.toString(), "input.zip").toFile();
        zipDirectory(tree, inputZipFile);

        File outputZipFile = Paths.get(zips.toString(), "output.zip").toFile();
        IoUtil.copyZipWithoutEmptyDirectories(inputZipFile, outputZipFile);
        try (ZipFile outputZip = new ZipFile(outputZipFile)) {
            Assert.assertNotNull(outputZip.getEntry("a/a.txt"));
            Assert.assertNotNull(outputZip.getEntry("a/"));
            Assert.assertNull(outputZip.getEntry("b/"));
            Assert.assertNull(outputZip.getEntry("c/"));
            Assert.assertNull(outputZip.getEntry("c/d/"));
        }
    }

    private static void zipDirectory(Path sourceDir, File zipFile) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile));
             Stream<Path> paths = Files.walk(sourceDir)
        ) {
            paths.forEach(path -> {
                String name = sourceDir.relativize(path).toString().replace('\\', '/');
                if (Files.isDirectory(path)) {
                    name = name + "/";
                }
                ZipEntry zipEntry = new ZipEntry(name);
                try {
                    zipOutputStream.putNextEntry(zipEntry);
                    if (!Files.isDirectory(path)) {
                        Files.copy(path, zipOutputStream);
                    }
                    zipOutputStream.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeIOException(e);
                }
            });
        }
    }


}
