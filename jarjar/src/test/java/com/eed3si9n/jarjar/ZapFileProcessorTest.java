package com.eed3si9n.jarjar;

import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import com.eed3si9n.jarjar.util.EntryStruct;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZapFileProcessorTest {
    @Test
    public void testZapFile() throws IOException {
        ZapFile zapFile = new ZapFile();
        zapFile.setPattern("org/**");
        ZapFileProcessor zapFileProcessor = new ZapFileProcessor(Collections.singletonList(zapFile));

        EntryStruct entryStruct = new EntryStruct();
        entryStruct.name = "org/example/Object.class";
        assertFalse(zapFileProcessor.process(entryStruct));

        entryStruct.name = "com/example/Object.class";
        assertTrue(zapFileProcessor.process(entryStruct));
    }

    @Test
    public void testZapFileNonClass() throws IOException {
        ZapFile zapFile = new ZapFile();
        zapFile.setPattern("org/**/*.proto");
        ZapFileProcessor zapFileProcessor = new ZapFileProcessor(Collections.singletonList(zapFile));

        EntryStruct entryStruct = new EntryStruct();
        entryStruct.name = "org/example/sub/sub.proto";
        assertFalse(zapFileProcessor.process(entryStruct));

        entryStruct.name = "com/example/main.proto";
        assertTrue(zapFileProcessor.process(entryStruct));

    }
}
