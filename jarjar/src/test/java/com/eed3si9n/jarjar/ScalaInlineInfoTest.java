package com.eed3si9n.jarjar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import junit.framework.TestCase;
import org.junit.Test;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
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

    /** Reads a .class file off the test classpath (from a resolved fixture dependency). */
    private static byte[] classpathFixture(String entry) throws IOException {
        InputStream in = ScalaInlineInfoTest.class.getClassLoader().getResourceAsStream(entry);
        assertNotNull(entry + " not on the test classpath", in);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            IoUtil.pipe(in, out, new byte[0x2000]);
            return out.toByteArray();
        } finally {
            in.close();
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

    @Test
    public void testShadeClassWithScalaInlineInfo() throws IOException {
        byte[] original = fixture("example/shapeless_2.12-2.3.2.jar", "shapeless/syntax/HListOps.class");
        Set<String> before = ScalaInlineInfoReader.methods(original);
        assertFalse("fixture should carry ScalaInlineInfo method entries", before.isEmpty());

        boolean preserved = before.equals(ScalaInlineInfoReader.methods(
            shade(original, "shapeless/syntax/HListOps.class", "shapeless.**", "shaded.shapeless.@1")));
        // Parsing and re-emitting ScalaInlineInfo through the rebuilt pool keeps
        // its references valid, so the method set survives shading intact
        // (scalameta/scalameta#3338).
        assertTrue(preserved);
    }

    /**
     * A Scala 2.11 trait interface carries a {@code ScalaInlineInfo} whose flags
     * set the {@code 0x2} self-type bit (dropped by Scala 2.12+). The fixture is
     * {@code argonaut.GeneratedEncodeJsons} from argonaut 6.2-RC2 (a resolved
     * test dependency). Consuming and re-emitting the self-type reference keeps
     * {@code numEntries} aligned, so the method set survives shading intact.
     */
    @Test
    public void testShadeScala211TraitWithSelfType() throws IOException {
        byte[] original = classpathFixture("argonaut/GeneratedEncodeJsons.class");
        Set<String> before = ScalaInlineInfoReader.methods(original);
        assertFalse("fixture should carry ScalaInlineInfo method entries", before.isEmpty());

        boolean preserved = before.equals(ScalaInlineInfoReader.methods(
            shade(original, "argonaut/GeneratedEncodeJsons.class", "argonaut.**", "shaded.argonaut.@1")));
        assertTrue(preserved);
    }

    /** A minimal class carrying {@code attr} bytes as its ScalaInlineInfo attribute. */
    private static byte[] classWithRawInlineInfo(final byte[] attr) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "pkg/Widget", null, "java/lang/Object", null);
        cw.visitAttribute(new Attribute("ScalaInlineInfo") {
            @Override
            protected ByteVector write(ClassWriter cw, byte[] code, int codeLen, int maxStack, int maxLocals) {
                return new ByteVector().putByteArray(attr, 0, attr.length);
            }
        });
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * A ScalaInlineInfo whose {@code 0x4} SAM flag points its name reference off
     * the constant pool: version 1, but a layout the reader can't resolve. It is
     * copied verbatim rather than crashing the transform.
     */
    @Test
    public void testUnrecognizedInlineInfoLayout() throws IOException {
        byte[] attr = { 1, 0x4, (byte) 0xff, (byte) 0xff, 0, 0 }; // version, flags=SAM, bogus samName, samDesc=0
        byte[] original = classWithRawInlineInfo(attr);
        assertNotNull(ScalaInlineInfoReader.attributeBytes(original));

        byte[] shaded = shade(original, "pkg/Widget.class", "pkg.**", "shaded.pkg.@1");
        assertTrue(Arrays.equals(attr, ScalaInlineInfoReader.attributeBytes(shaded)));
    }
}
