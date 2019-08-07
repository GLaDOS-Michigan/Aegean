/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value;

import merkle.MerkleTreeInstance;
import org.h2.constant.SysProperties;
import org.h2.util.MathUtils;
import org.h2.util.StringCache;

/**
 * Implementation of the VARCHAR data type.
 */
public class ValueString extends ValueStringBase implements merkle.MerkleTreeObject, merkle.MerkleTreeCloneable {

    static {
        MerkleTreeInstance.addStatic(ValueString.class);
        //MerkleTreeInstance.add("");
        EMPTY = new ValueString("");
    }

    public static ValueString EMPTY;

    private int MTIdentifier = -1;

    @Override
    public void setMTIdentifier(int identifier) {
        this.MTIdentifier = identifier;
    }

    @Override
    public int getMTIdentifier() {
        return this.MTIdentifier;
    }

    public void copyObject(Object dst) {
        ValueString target = (ValueString) dst;
        target.value = this.value;
    }

    public Object cloneObject() {
        ValueString target = new ValueString();
        this.copyObject(target);
        return target;
    }

    public ValueString() {

    }

    protected ValueString(String value) {
        super(value);
        //MerkleTreeInstance.add(this);
    }

    public int getType() {
        return Value.STRING;
    }

    protected int compareSecure(Value o, CompareMode mode) {
        // compatibility: the other object could be ValueStringFixed
        ValueStringBase v = (ValueStringBase) o;
        return mode.compareString(value, v.value, false);
    }

    public boolean equals(Object other) {
        return other instanceof ValueStringBase
                && value.equals(((ValueStringBase) other).value);
    }

    // TODO: removed the implementation of hashcode for Merkle Trees.
    // public int hashCode()
    // {
    // return value.hashCode();
    // }

    public Value convertPrecision(long precision) {
        if (precision == 0 || value.length() <= precision) {
            return this;
        }
        int p = MathUtils.convertLongToInt(precision);
        return ValueString.get(value.substring(0, p));
    }

    /**
     * Get or create a string value for the given string.
     *
     * @param s the string
     * @return the value
     */
    public static ValueString get(String s) {
        if (s.length() == 0) {
            return EMPTY;
        }
        ValueString obj = new ValueString(StringCache.get(s));
        if (s.length() > SysProperties.OBJECT_CACHE_MAX_PER_ELEMENT_SIZE) {
            return obj;
        }
        return (ValueString) Value.cache(obj);
        // this saves memory, but is really slow
        // return new ValueString(s.intern());
    }

}
