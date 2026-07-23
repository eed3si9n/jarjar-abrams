package com.eed3si9n.jarjar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import junit.framework.TestCase;
import org.junit.Test;
import com.eed3si9n.jarjar.util.EntryStruct;
import com.eed3si9n.jarjar.util.IoUtil;
import com.eed3si9n.jarjar.util.JarTransformerChain;
import com.eed3si9n.jarjar.util.RemappingClassTransformer;

/**
 * Shading a class that carries Scala's {@code ScalaInlineInfo} attribute.
 *
 * <p>The fixture is {@code shapeless.syntax.HListOps} taken from the shapeless
 * 2.3.2 jar already vendored under {@code example/} (Scala 2.12, so the
 * attribute is present). The attribute names methods by constant-pool index;
 * when jarjar rewrites a class it rebuilds the pool, so those indices must be
 * re-emitted or they dangle and the Scala 2.12/2.13 inliner fails reading the
 * shaded class (scalameta/scalameta#3338).
 */
public class ScalaInlineInfoTest extends TestCase {

    private static byte[] fixture(String jar, String entry) throws IOException {
        ZipFile zf = new ZipFile(jar);
        try {
            ZipEntry ze = zf.getEntry(entry);
            assertNotNull(entry + " missing from " + jar, ze);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            IoUtil.pipe(zf.getInputStream(ze), out, new byte[0x2000]);
            return out.toByteArray();
        } finally {
            zf.close();
        }
    }

    private static byte[] shade(byte[] classBytes, String entry, String pattern, String result) throws IOException {
        EntryStruct e = new EntryStruct();
        e.name = entry;
        e.skipTransform = false;
        e.time = 0;
        e.data = classBytes;
        // Rename the fixture's own package so jarjar rewrites the class and
        // rebuilds its constant pool; classes it need not touch are copied
        // verbatim, leaving the attribute trivially intact.
        Rule rule = new Rule();
        rule.setPattern(pattern);
        rule.setResult(result);
        PackageRemapper pr = new PackageRemapper(Arrays.asList(rule), false);
        new JarTransformerChain(new RemappingClassTransformer[] { new RemappingClassTransformer() }, pr)
            .process(e);
        return e.data;
    }

    /**
     * The method name+descriptor pairs recoverable from the class's
     * ScalaInlineInfo attribute, resolving each reference against that same
     * class's constant pool. References that fall out of range or land on a
     * non-UTF8 entry (i.e. dangling after a pool rebuild) are dropped.
     */
    private static Set<String> inlineInfoMethods(byte[] b) {
        int cpCount = u2(b, 8);
        int[] tag = new int[cpCount];
        String[] utf8 = new String[cpCount];
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
        off = skipMembers(b, off); // methods
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

    @Test
    public void testShadeClassWithScalaInlineInfo() throws IOException {
        byte[] original = fixture("example/shapeless_2.12-2.3.2.jar", "shapeless/syntax/HListOps.class");
        Set<String> before = inlineInfoMethods(original);
        assertFalse("fixture should carry ScalaInlineInfo method entries", before.isEmpty());

        boolean preserved = before.equals(
            inlineInfoMethods(shade(original, "shapeless/syntax/HListOps.class", "shapeless.**", "shaded.shapeless.@1")));
        // Parsing and re-emitting ScalaInlineInfo through the rebuilt pool keeps
        // its references valid, so the method set survives shading intact
        // (scalameta/scalameta#3338).
        assertTrue(preserved);
    }
}
