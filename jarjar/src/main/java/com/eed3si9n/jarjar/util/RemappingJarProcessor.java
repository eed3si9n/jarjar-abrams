package com.eed3si9n.jarjar.util;


import com.eed3si9n.jarjar.TracingRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;

/**
 * Extension of JarProcessor for building processors around TracingRemapper.
 *
 * This class will track if TracingRemapper recorded doing any changes, and if it didn't,
 * it will make sure that the underlying bytes are unmodified.
 *
 * This helps to avoid bytecode modifications when there was no actual changes
 * to do.
 */
public abstract class RemappingJarProcessor implements JarProcessor {
    private TracingRemapper remapper;

    public RemappingJarProcessor(TracingRemapper remapper) {
        this.remapper = remapper;
    }

    public final boolean process(EntryStruct struct) throws IOException {
        TracingRemapper structRemapper = remapper.copy();
        EntryStruct origStruct = struct.copy();

        if (!processImpl(struct, structRemapper)) {
            return false;
        }

        if (!structRemapper.hasChanges()) {
            struct.data = origStruct.data;
        }

        return true;
    }

    protected abstract boolean processImpl(EntryStruct struct, Remapper remapper) throws IOException;
}