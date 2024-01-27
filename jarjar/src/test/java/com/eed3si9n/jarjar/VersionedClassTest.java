package com.eed3si9n.jarjar;

import com.eed3si9n.jarjar.example.Example;
import com.eed3si9n.jarjar.misplaced.MisplacedClassProcessor;
import com.eed3si9n.jarjar.util.EntryStruct;
import com.eed3si9n.jarjar.util.IoUtil;
import com.eed3si9n.jarjar.util.JarTransformer;
import junit.framework.TestCase;
import org.junit.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class VersionedClassTest
extends TestCase
{
    private String toClassPath(String name) {
        return name.replace(".", "/") + ".class";
    }

    private byte[] getClassBytes(String name) throws IOException {
        byte[] buf = new byte[0x2000];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream in = getClass().getClassLoader().getResourceAsStream(toClassPath(name));
        IoUtil.pipe(in, baos, buf);
        return baos.toByteArray();
    }

    @Test
    public void testVersionPrefixIsPreserved() throws IOException {
        EntryStruct struct = new EntryStruct();
        String originalName = Example.class.getName();
        String expectedName = Example.class.getName().replace("jarjar", "jarjarabrams");
        struct.name = MisplacedClassProcessor.VERSIONED_CLASS_FOLDER + "9/" + toClassPath(originalName);
        struct.data = getClassBytes(originalName);

        Rule rule = new Rule();
        rule.setPattern(originalName);
        rule.setResult(expectedName);
        PackageRemapper remapper = new PackageRemapper(Arrays.asList(rule), false);

        JarTransformer transformer = new JarTransformer(remapper) {
            @Override
            protected ClassVisitor transform(ClassVisitor v, Remapper remapper) {
                return new ClassRemapper(v, remapper);
            }
        };
        transformer.process(struct);
        assertEquals(struct.name, MisplacedClassProcessor.VERSIONED_CLASS_FOLDER + "9/" + toClassPath(expectedName));
    }

    @Test
    public void testVersionedClassIsNotMisplaced() throws IOException {
        EntryStruct struct = new EntryStruct();
        struct.name = MisplacedClassProcessor.VERSIONED_CLASS_FOLDER + "9/" + toClassPath(Example.class.getName());
        struct.data = getClassBytes(Example.class.getName());
        MisplacedClassProcessor processor = new MisplacedClassProcessor() {
            @Override
            public void handleMisplacedClass(EntryStruct classStruct, String className) {
                fail("This method should not be called");
            }

            @Override
            public boolean shouldTransform() {
                return true;
            }

            @Override
            public boolean shouldKeep() {
                return true;
            }
        };
        processor.process(struct);
    }
}
