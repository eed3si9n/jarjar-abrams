package com.eed3si9n.jarjar;

import com.eed3si9n.jarjar.util.EntryStruct;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class ResourceProcessorTest {

    private ResourceProcessor processor;

    @Before
    public void setUp() throws Exception {
        String rules = "rule org.example.** something.shaded.@0";
        List<Rule> parsed = (List<Rule>)(List<?>) RulesFileParser.parse(rules);
        processor = new ResourceProcessor(new PackageRemapper(parsed, true));
    }

    @Test
    public void testClassFile() throws IOException {
        EntryStruct entryStruct = new EntryStruct();
        entryStruct.name = "org/example/Object.class";
        entryStruct.data = new byte[]{0x10};

        assertTrue(processor.process(entryStruct));
        assertEquals(entryStruct.name, "org/example/Object.class");
        assertArrayEquals(entryStruct.data, new byte[]{0x10});
    }

    @Test
    public void testServiceProviderConfig() throws IOException {
        String original = "org.example.Impl     # comment\n"
                        + "org.example.AnotherImpl\n"
                        + "#\n"
                        + "\n"
                        + "     org.another.Impl";
        String expected = "something.shaded.org.example.Impl\n"
                        + "something.shaded.org.example.AnotherImpl\n"
                        + "\n"
                        + "\n"
                        + "org.another.Impl\n";
        EntryStruct entryStruct = new EntryStruct();
        entryStruct.name = "META-INF/services/org.example.Service";
        entryStruct.data = original.getBytes(StandardCharsets.UTF_8);

        assertTrue(processor.process(entryStruct));
        assertEquals(entryStruct.name, "META-INF/services/something.shaded.org.example.Service");
        assertArrayEquals(entryStruct.data, expected.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testOtherResource() throws IOException {
        EntryStruct entryStruct = new EntryStruct();
        entryStruct.name = "org/example/file.txt";
        entryStruct.data = new byte[]{0x10};

        assertTrue(processor.process(entryStruct));
        assertEquals(entryStruct.name, "something/shaded/org/example/file.txt");
        assertArrayEquals(entryStruct.data, new byte[]{0x10});

        EntryStruct entryStruct2 = new EntryStruct();
        entryStruct2.name = "another_org/example/file.txt";
        entryStruct2.data = new byte[]{0x10};

        assertTrue(processor.process(entryStruct2));
        assertEquals(entryStruct2.name, "another_org/example/file.txt");
        assertArrayEquals(entryStruct.data, new byte[]{0x10});
    }
}