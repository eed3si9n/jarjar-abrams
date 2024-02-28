/**
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eed3si9n.jarjar.util;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

// Visible for testing
public class IoUtil {
    private IoUtil() {}

    public static void pipe(InputStream is, OutputStream out, byte[] buf) throws IOException {
        for (;;) {
            int amt = is.read(buf);
            if (amt < 0)
                break;
            out.write(buf, 0, amt);
        }
    }

    public static void copy(File from, File to, byte[] buf) throws IOException {
        InputStream in = new FileInputStream(from);
        try {
            OutputStream out = new FileOutputStream(to);
            try {
                pipe(in, out, buf);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    /**
     * Create a copy of a zip file, removing all empty directories.
     * @param inputFile
     * @param outputFile
     * @throws IOException
     */
    public static void copyZipWithoutEmptyDirectories(File inputFile, File outputFile) throws IOException {
        final byte[] buf = new byte[0x2000];
        ZipFile inputZip = new ZipFile(inputFile);
        ZipOutputStream outputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)));

        // First pass: create a set of directories to retain
        Set<String> dirsToRetain = new HashSet<>();
        Enumeration<? extends ZipEntry> entries = inputZip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                String name = entry.getName();
                int index = name.lastIndexOf('/');
                while (index > 0) {
                    name = name.substring(0, index);
                    dirsToRetain.add(name + "/");
                    index = name.lastIndexOf('/');
                }
            }
        }

        // Second pass: copy entries, excluding directories not in the set
        entries = inputZip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry inputEntry = entries.nextElement();
            if (!inputEntry.isDirectory() || dirsToRetain.contains(inputEntry.getName())) {
                ZipEntry outputEntry = new ZipEntry(inputEntry);
                outputEntry.setCompressedSize(-1);
                outputStream.putNextEntry(outputEntry);
                if (!inputEntry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    final InputStream is = inputZip.getInputStream(inputEntry);
                    IoUtil.pipe(is, baos, buf);
                    is.close();
                    outputStream.write(baos.toByteArray());
                }
                outputStream.closeEntry();
            }
        }

        outputStream.close();
        inputZip.close();
    }

}
