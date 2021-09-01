package com.eed3si9n.jarjar;

import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import com.eed3si9n.jarjar.util.EntryStruct;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeepProcessorTest
{
    @Test
    public void testKeep() throws IOException {
        Keep keep = new Keep();
        keep.setPattern("org.**");
        KeepProcessor keepProcessor = new KeepProcessor(Collections.singletonList(keep));

        EntryStruct entryStruct = new EntryStruct();
        entryStruct.name = "org/example/Object.class";
        assertTrue(keepProcessor.process(entryStruct));

        entryStruct.name = "com/example/Object.class";
        assertFalse(keepProcessor.process(entryStruct));
    }

    @Test
    public void testMetaInfKeep() throws IOException {
        Keep keep = new Keep();
        keep.setPattern("META-INF.versions.9.**");
        KeepProcessor keepProcessor = new KeepProcessor(Collections.singletonList(keep));

        EntryStruct entryStruct = new EntryStruct();
        entryStruct.name = "META-INF/versions/9/org/example/Object.class";
        assertTrue(keepProcessor.process(entryStruct));

        entryStruct.name = "META-INF/versions/8/com/example/Object.class";
        assertFalse(keepProcessor.process(entryStruct));
    }
}