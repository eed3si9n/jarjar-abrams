package com.eed3si9n.jarjar;

import com.eed3si9n.jarjar.util.EntryStruct;
import com.eed3si9n.jarjar.util.JarProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ZapFileProcessor implements JarProcessor {
    private List<PathWildcard> pathWildcards;

    public ZapFileProcessor(List<ZapFile> zapFiles) {
        pathWildcards = createFilePathWildcards(zapFiles);
    }

    private List<PathWildcard> createFilePathWildcards(List<ZapFile> zapFiles) {
        List<PathWildcard> wildcards = new ArrayList<>();
        for (ZapFile zapFile : zapFiles) {
            wildcards.add(new PathWildcard(zapFile.getPattern(), ""));
        }
        return wildcards;
    }

    @Override
    public boolean process(EntryStruct struct) throws IOException {
        String filepath = struct.name;
        for (PathWildcard pathWildcard : pathWildcards) {
            if (pathWildcard.matches(filepath))
                return false;
        }
        return true;
    }
}
