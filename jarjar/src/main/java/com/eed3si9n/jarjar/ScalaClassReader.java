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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

/**
 * A {@link ClassReader} that parses {@link ScalaInlineInfoAttribute} instead of
 * leaving ASM to copy it verbatim. A processor that writes the class back writes
 * through a constant pool ASM rebuilds, and an attribute copied verbatim still
 * names the pool it was read from.
 */
public class ScalaClassReader extends ClassReader {

    public ScalaClassReader(byte[] classFile) {
        super(classFile);
    }

    public void accept(ClassVisitor visitor) {
        accept(visitor, new Attribute[] { new ScalaInlineInfoAttribute() }, EXPAND_FRAMES);
    }
}
