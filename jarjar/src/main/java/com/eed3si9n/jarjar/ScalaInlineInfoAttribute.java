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

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;

/**
 * ASM prototype for the Scala compiler's {@code ScalaInlineInfo} class
 * attribute.
 *
 * <p>The attribute stores method names and descriptors as constant-pool UTF8
 * references. ASM treats attributes it doesn't recognize as opaque byte blobs
 * and copies them verbatim; because jarjar rebuilds the constant pool of every
 * class it rewrites, those raw indices go stale and the Scala 2.12/2.13
 * inliner later fails reading the shaded class with
 * {@code Error while reading InlineInfoAttribute ... Index N out of bounds for
 * length N}. Registering this prototype makes ASM parse the references against
 * the source pool and re-emit them through the destination {@link ClassWriter},
 * so they stay valid and the inline info is preserved rather than dropped.
 *
 * <p>Layout (see {@code scala.tools.nsc.backend.jvm.opt.InlineInfoAttribute}):
 * <pre>
 *   u1 version
 *   u1 flags                       // 0x1 final, 0x2 self type, 0x4 SAM, 0x8 late interfaces
 *   [u2 selfType]                  // present iff (flags &amp; 0x2)
 *   [u2 samName; u2 samDesc]       // present iff (flags &amp; 0x4)
 *   u2 numEntries
 *   numEntries * { u2 name; u2 desc; u1 methodFlags }
 * </pre>
 *
 * <p>The {@code 0x2} self type reference (a trait's {@code traitImplClassSelfType})
 * is no longer emitted by Scala 2.12+, but classes compiled by Scala 2.11 — e.g.
 * a trait interface like {@code argonaut.GeneratedEncodeJsons} — still carry it.
 * Skipping it misreads {@code numEntries} from the wrong offset and walks the
 * reader off the constant pool. Its value is unused by the inliner, but the u2
 * must be consumed and re-emitted to keep the layout valid.
 *
 * <p>The descriptor strings are copied unchanged: they name the class's own
 * members, and on the rare method whose descriptor mentions a relocated type
 * the entry simply no longer matches that method, so the inliner falls back to
 * info derived from the bytecode. That is a conservative loss of a hint, never
 * an incorrect inline.
 *
 * <p>Only {@code version} 1 is understood, and only a layout that parses cleanly
 * and consumes exactly the attribute length is re-emitted. Anything else — a
 * future version, a v1 layout that doesn't add up, or references that run off
 * the pool — is left byte-for-byte as-is (a verbatim copy) rather than
 * misparsed, so this stays a strict improvement even if Scala's layout evolves.
 */
public class ScalaInlineInfoAttribute extends Attribute {

    private static final int VERSION = 1;
    private static final int HAS_SELF = 0x2;
    private static final int HAS_SAM = 0x4;

    private int version;
    private int flags;
    private String selfType;
    private String samName;
    private String samDesc;
    private String[] names;
    private String[] descs;
    private int[] methodFlags;
    // Non-null iff version is unrecognized: the original bytes, re-emitted as-is.
    private byte[] verbatim;

    public ScalaInlineInfoAttribute() {
        super("ScalaInlineInfo");
    }

    private ScalaInlineInfoAttribute(int version, int flags, String selfType, String samName, String samDesc,
                                     String[] names, String[] descs, int[] methodFlags, byte[] verbatim) {
        super("ScalaInlineInfo");
        this.version = version;
        this.flags = flags;
        this.selfType = selfType;
        this.samName = samName;
        this.samDesc = samDesc;
        this.names = names;
        this.descs = descs;
        this.methodFlags = methodFlags;
        this.verbatim = verbatim;
    }

    @Override
    protected Attribute read(ClassReader cr, int off, int len, char[] buf, int codeOff, Label[] labels) {
        int version = cr.readByte(off);
        if (version == VERSION) {
            try {
                ScalaInlineInfoAttribute parsed = readVersion1(cr, off, len, buf);
                if (parsed != null) return parsed;
            } catch (RuntimeException layoutNotUnderstood) {
                // References ran off the constant pool: the layout isn't one we
                // model. Fall through to the verbatim copy below.
            }
        }
        byte[] raw = new byte[len];
        for (int i = 0; i < len; i++) raw[i] = (byte) cr.readByte(off + i);
        return new ScalaInlineInfoAttribute(version, 0, null, null, null, null, null, null, raw);
    }

    private ScalaInlineInfoAttribute readVersion1(ClassReader cr, int off, int len, char[] buf) {
        int p = off + 1;
        int flags = cr.readByte(p); p += 1;
        String selfType = null;
        if ((flags & HAS_SELF) != 0) {
            selfType = cr.readUTF8(p, buf); p += 2;
        }
        String samName = null;
        String samDesc = null;
        if ((flags & HAS_SAM) != 0) {
            samName = cr.readUTF8(p, buf); p += 2;
            samDesc = cr.readUTF8(p, buf); p += 2;
        }
        int n = cr.readUnsignedShort(p); p += 2;
        String[] names = new String[n];
        String[] descs = new String[n];
        int[] methodFlags = new int[n];
        for (int i = 0; i < n; i++) {
            names[i] = cr.readUTF8(p, buf); p += 2;
            descs[i] = cr.readUTF8(p, buf); p += 2;
            methodFlags[i] = cr.readByte(p); p += 1;
        }
        // A layout we understand is consumed exactly. If our offsets disagree
        // with the declared length, this class uses a format we don't model;
        // return null so read() copies it verbatim rather than re-emit a
        // truncated/mis-encoded attribute.
        if (p - off != len) return null;
        return new ScalaInlineInfoAttribute(VERSION, flags, selfType, samName, samDesc, names, descs, methodFlags, null);
    }

    @Override
    protected ByteVector write(ClassWriter cw, byte[] code, int codeLen, int maxStack, int maxLocals) {
        ByteVector b = new ByteVector();
        if (verbatim != null) {
            b.putByteArray(verbatim, 0, verbatim.length);
            return b;
        }
        b.putByte(version);
        b.putByte(flags);
        if ((flags & HAS_SELF) != 0) {
            b.putShort(ref(cw, selfType));
        }
        if ((flags & HAS_SAM) != 0) {
            b.putShort(ref(cw, samName));
            b.putShort(ref(cw, samDesc));
        }
        b.putShort(names.length);
        for (int i = 0; i < names.length; i++) {
            b.putShort(ref(cw, names[i]));
            b.putShort(ref(cw, descs[i]));
            b.putByte(methodFlags[i]);
        }
        return b;
    }

    private static int ref(ClassWriter cw, String value) {
        return value == null ? 0 : cw.newUTF8(value);
    }
}
