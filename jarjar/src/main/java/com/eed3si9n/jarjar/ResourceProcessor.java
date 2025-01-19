/**
 * Copyright 2007 Google Inc.
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

import com.eed3si9n.jarjar.util.EntryStruct;
import com.eed3si9n.jarjar.util.JarProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

class ResourceProcessor implements JarProcessor
{
    private final static String META_INF_SERVICES = "META-INF/services/";
    private PackageRemapper pr;

    public ResourceProcessor(PackageRemapper pr) {
        this.pr = pr;
    }

    public boolean process(EntryStruct struct) throws IOException {
        switch (identify(struct)) {
            case CLASS_FILE:
                break;
            case SERVICE_PROVIDER_CONFIGURATION:
                struct.name = remapService(struct.name);
                struct.data = remapServiceProviders(struct.data);
                break;
            case OTHER:
                struct.name = pr.mapPath(struct.name);
                break;
        }
        return true;
    }

    private String remapService(String serviceFile) {
        int idx = serviceFile.lastIndexOf('/');
        return META_INF_SERVICES + pr.mapValue(serviceFile.substring(idx + 1));
    }

    private byte[] remapServiceProviders(byte[] providers) {
        // Provider configuration is encoded in UTF-8
        // The file can also have comments and whitespaces
        String s = new String(providers, StandardCharsets.UTF_8);

        String mapped = Arrays.stream(s.split("\n"))
            .map(l -> (String) pr.mapValue(Arrays.stream(l.split("#")).findFirst().orElse("").trim()))
            .collect(Collectors.joining("\n")) + "\n";
        return mapped.getBytes(StandardCharsets.UTF_8);
    }

    private Resource identify(EntryStruct struct) {
        if (struct.name.endsWith(".class"))
            return Resource.CLASS_FILE;
        // https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ServiceLoader.html
        if (struct.name.startsWith(META_INF_SERVICES) && !struct.name.equals(META_INF_SERVICES))
            return Resource.SERVICE_PROVIDER_CONFIGURATION;
        return Resource.OTHER;
    }

    private enum Resource {
        CLASS_FILE,
        SERVICE_PROVIDER_CONFIGURATION,
        OTHER
    }
}
    
