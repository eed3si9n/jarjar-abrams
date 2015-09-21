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

package org.pantsbuild.jarjar.util;

import java.io.IOException;

public interface JarProcessor
{
    /**
     * Process the entry (p.ex. rename the file)
     * <p>
     * Returns <code>true</code> if the processor "successfully" processed the entry. In practice,
     * "true" is used to indicate that the entry should be kept, and false is used to indicate the
     * entry should be thrown away, and doesn't indicate anything in particular about what this
     * JarProcessor actually did to the entry (if anything).
     *
     * @param struct The jar entry.
     * @return <code>true</code> if the process chain can continue after this process
     * @throws IOException
     */
    boolean process(EntryStruct struct) throws IOException;
}
