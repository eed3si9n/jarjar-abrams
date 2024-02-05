package com.eed3si9n.jarjar;

import org.objectweb.asm.commons.Remapper;

public abstract class TracingRemapper extends Remapper {
    /**
     * Obtain the fresh copy of this object.
     */
    public abstract TracingRemapper copy();

    /**
     * Check if this instance remapped something already.
     */
    public abstract boolean hasChanges();
}
