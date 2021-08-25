package org.pantsbuild.jarjar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import junit.framework.TestCase;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.pantsbuild.jarjar.util.EntryStruct;
import org.pantsbuild.jarjar.util.IoUtil;

public class MethodRewriterTest extends TestCase {

  private static class VerifyingClassVisitor extends ClassVisitor {

    private static class VerifyingMethodVisitor extends MethodVisitor {
      private VerifyingMethodVisitor() {
        super(Opcodes.ASM7);
      }

      @Override
      public void visitLdcInsn(Object value) {
        if (value instanceof String) {
          assertFalse(((String) value).contains("com.google."));
          assertFalse("Value was: " + value, ((String) value).contains("com/google/"));
        }
      }
    }

    private VerifyingClassVisitor() {
      super(Opcodes.ASM7);
    }

    @Override
    public MethodVisitor visitMethod(
        int access,
        java.lang.String name,
        java.lang.String descriptor,
        java.lang.String signature,
        java.lang.String[] exceptions) {
      return new VerifyingMethodVisitor();
    }
  }

  private static byte[] readInputStream(InputStream inputStream) throws IOException {
    byte[] buf = new byte[0x2000];
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    IoUtil.pipe(inputStream, baos, buf);
    return baos.toByteArray();
  }

  @Test
  public void testRewriteMethod() throws IOException {
    EntryStruct entryStruct = new EntryStruct();
    entryStruct.name = "BigtableIO$Write.class";
    entryStruct.skipTransform = false;
    entryStruct.time = 0;
    entryStruct.data =
        readInputStream(
            getClass().getResourceAsStream("/org/pantsbuild/jarjar/BigtableIO$Write.class"));
    Rule rule = new Rule();
    rule.setPattern("com.google.**");
    rule.setResult("com.googleshaded.@1");
    new MethodSignatureProcessor(new PackageRemapper(Arrays.asList(rule), false))
        .process(entryStruct);
    new ClassReader(entryStruct.data)
        .accept(new VerifyingClassVisitor(), ClassReader.EXPAND_FRAMES);
  }
}
