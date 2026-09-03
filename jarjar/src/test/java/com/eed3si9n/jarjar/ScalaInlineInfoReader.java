/**
 * Copyright 2024 eed3si9n
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

package com.eed3si9n.jarjar;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads a class's {@code ScalaInlineInfo} attribute straight out of the
 * classfile bytes, without ASM. A test that asks whether shading kept the
 * attribute intact has to resolve its references against the pool the shaded
 * class actually carries; going through ASM would resolve them against the
 * prototype under test instead.
 */
public class ScalaInlineInfoReader {

    /**
     * The method name+descriptor pairs recoverable from the class's
     * ScalaInlineInfo attribute, resolving each reference against that same
     * class's constant pool. References that fall out of range or land on a
     * non-UTF8 entry (i.e. dangling after a pool rebuild) are dropped.
     */
    public static Set<String> methods(byte[] b) {
        int cpCount = u2(b, 8);
        int[] tag = new int[cpCount];
        String[] utf8 = new String[cpCount];
        int off = readConstantPool(b, tag, utf8);
        Set<String> methods = new LinkedHashSet<String>();
        int nAttrs = u2(b, off);
        off += 2;
        for (int i = 0; i < nAttrs; i++) {
            int nameIdx = u2(b, off);
            int len = u4(b, off + 2);
            int data = off + 6;
            if ("ScalaInlineInfo".equals(utf8[nameIdx]) && (b[data] & 0xff) == 1) {
                int p = data + 1;
                int flags = b[p] & 0xff;
                p += 1;
                if ((flags & 0x2) != 0) p += 2; // self type: not a method entry
                if ((flags & 0x4) != 0) p += 4; // SAM name+desc: not a method entry
                int n = u2(b, p);
                p += 2;
                for (int m = 0; m < n; m++) {
                    String name = resolve(tag, utf8, cpCount, u2(b, p));
                    String desc = resolve(tag, utf8, cpCount, u2(b, p + 2));
                    p += 5;
                    if (name != null && desc != null) methods.add(name + desc);
                }
            }
            off = data + len;
        }
        return methods;
    }

    /** The raw payload bytes of the class's ScalaInlineInfo attribute, or null. */
    public static byte[] attributeBytes(byte[] b) {
        int cpCount = u2(b, 8);
        int[] tag = new int[cpCount];
        String[] utf8 = new String[cpCount];
        int off = readConstantPool(b, tag, utf8);
        int nAttrs = u2(b, off);
        off += 2;
        for (int i = 0; i < nAttrs; i++) {
            int nameIdx = u2(b, off);
            int len = u4(b, off + 2);
            int data = off + 6;
            if ("ScalaInlineInfo".equals(utf8[nameIdx])) return Arrays.copyOfRange(b, data, data + len);
            off = data + len;
        }
        return null;
    }

    /** Fills in the pool's tags and UTF8 values, and returns the offset of the class attributes. */
    private static int readConstantPool(byte[] b, int[] tag, String[] utf8) {
        int cpCount = tag.length;
        int off = 10;
        for (int i = 1; i < cpCount; i++) {
            tag[i] = b[off++] & 0xff;
            switch (tag[i]) {
                case 1: {
                    int len = u2(b, off);
                    off += 2;
                    utf8[i] = new String(b, off, len, StandardCharsets.UTF_8);
                    off += len;
                    break;
                }
                case 7: case 8: case 16: case 19: case 20: off += 2; break;
                case 15: off += 3; break;
                case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18: off += 4; break;
                case 5: case 6: off += 8; i++; break; // long/double take two slots
                default: throw new IllegalStateException("unexpected constant pool tag " + tag[i]);
            }
        }
        off += 6; // access_flags, this_class, super_class
        off += 2 + 2 * u2(b, off); // interfaces
        off = skipMembers(b, off); // fields
        return skipMembers(b, off); // methods
    }

    private static String resolve(int[] tag, String[] utf8, int cpCount, int idx) {
        return (idx >= 1 && idx < cpCount && tag[idx] == 1) ? utf8[idx] : null;
    }

    private static int skipMembers(byte[] b, int off) {
        int n = u2(b, off);
        off += 2;
        for (int i = 0; i < n; i++) {
            int a = u2(b, off + 6); // access, name, descriptor, then attribute_count
            off += 8;
            for (int j = 0; j < a; j++) off += 6 + u4(b, off + 2);
        }
        return off;
    }

    private static int u2(byte[] b, int p) {
        return ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff);
    }

    private static int u4(byte[] b, int p) {
        return ((b[p] & 0xff) << 24) | ((b[p + 1] & 0xff) << 16) | ((b[p + 2] & 0xff) << 8) | (b[p + 3] & 0xff);
    }
}
