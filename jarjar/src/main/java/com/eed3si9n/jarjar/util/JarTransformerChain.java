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

package com.eed3si9n.jarjar.util;

import com.eed3si9n.jarjar.TracingRemapper;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.Remapper;

public class JarTransformerChain extends JarTransformer
{
    private final RemappingClassTransformer[] chain;

    public JarTransformerChain(RemappingClassTransformer[] chain, TracingRemapper remapper) {
        super(remapper);
        this.chain = chain.clone();
    }

    protected ClassVisitor transform(ClassVisitor parent, Remapper remapper) {
        for (int i = chain.length - 1; i >= 0; i--) {
            chain[i] = chain[i].update(remapper, parent);
            parent = chain[i];
        }
        return parent;
    }
}
