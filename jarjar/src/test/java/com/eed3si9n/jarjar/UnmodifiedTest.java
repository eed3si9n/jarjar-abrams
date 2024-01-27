package com.eed3si9n.jarjar;

import com.eed3si9n.jarjar.util.EntryStruct;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.eed3si9n.jarjar.MethodRewriterTest.readInputStream;

public class UnmodifiedTest {
    @Test
    public void testNotModified() throws Exception {
        Rule rule = new Rule();
        rule.setPattern("com.abc");
        rule.setResult("com.def");

        MainProcessor mp = new MainProcessor(Arrays.asList(rule), false, false, "move");

        EntryStruct entryStruct = new EntryStruct();
        entryStruct.name = "BigtableIO$Write.class";
        entryStruct.skipTransform = false;
        entryStruct.time = 0;
        entryStruct.data =
                readInputStream(
                        getClass().getResourceAsStream("/com/eed3si9n/jarjar/BigtableIO$Write.class"));

        EntryStruct orig = entryStruct.copy();

        mp.process(entryStruct);

        Assert.assertEquals(entryStruct.data, orig.data);
    }
}