/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value;

import merkle.MerkleTreeInstance;
import org.h2.constant.ErrorCode;
import org.h2.constant.SysProperties;
import org.h2.message.Message;
import org.h2.util.MathUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Implementation of the INT data type.
 */
public class ValueInt extends Value implements merkle.MerkleTreeObject, merkle.MerkleTreeCloneable {

    /**
     * The precision in digits.
     */
    public static final int PRECISION = 10;

    /**
     * The maximum display size of an int. Example: -2147483648
     */
    public static final int DISPLAY_SIZE = 11;

    // ORIGINAL SIZE = 128
    // private static final int STATIC_SIZE = 8192;
    private static final int STATIC_SIZE = 8192;
    // must be a power of 2
    // ORIGINAL SIZE = 256
    private static final int DYNAMIC_SIZE = 8192;
    public transient static final ValueInt[] STATIC_CACHE = new ValueInt[STATIC_SIZE];
    //public transient static final ValueInt[] DYNAMIC_CACHE = new ValueInt[DYNAMIC_SIZE];

    private int value;

    static {
        MerkleTreeInstance.addStatic(ValueInt.class);
        //MerkleTreeInstance.add(ValueInt.STATIC_CACHE);
        //MerkleTreeInstance.add(ValueInt.DYNAMIC_CACHE);
        for (int i = 0; i < STATIC_SIZE; i++) {
            STATIC_CACHE[i] = new ValueInt(i);
            MerkleTreeInstance.add(STATIC_CACHE[i]);
        }
    }

    private int MTIdentifier = -1;

    @Override
    public void setMTIdentifier(int identifier) {
        this.MTIdentifier = identifier;
    }

    @Override
    public int getMTIdentifier() {
        return this.MTIdentifier;
    }

    @Override
    public void copyObject(Object dst) {
        ValueInt target = (ValueInt) dst;
        target.value = this.value;
    }

    @Override
    public Object cloneObject() {
        ValueInt target = new ValueInt();
        this.copyObject(target);
        return target;
    }

    public ValueInt() {

    }

    private ValueInt(int value) {
        this.value = value;
    }

    /**
     * Get or create an int value for the given int.
     *
     * @param i the int
     * @return the value
     */
    public static ValueInt get(int i) {
        if (i >= 0 && i < STATIC_SIZE) {
            return STATIC_CACHE[i];
        }
        /*ValueInt v = DYNAMIC_CACHE[i & (DYNAMIC_SIZE - 1)];
		if (v == null || v.value != i)
		{
			if (v != null && v.value != i)
			{
				// TODO: MerkleTree garbage collect value object
				// System.out.println("eh oui, dommage");
			}
			v = new ValueInt(i);
			MerkleTreeInstance.add(v);
			DYNAMIC_CACHE[i & (DYNAMIC_SIZE - 1)] = v;
		}*/
        return new ValueInt(i);
    }

    public Value add(Value v) throws SQLException {
        ValueInt other = (ValueInt) v;
        if (SysProperties.OVERFLOW_EXCEPTIONS) {
            return checkRange((long) value + (long) other.value);
        }
        return ValueInt.get(value + other.value);
    }

    private ValueInt checkRange(long x) throws SQLException {
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE) {
            throw Message.getSQLException(ErrorCode.OVERFLOW_FOR_TYPE_1,
                    DataType.getDataType(Value.INT).name);
        }
        return ValueInt.get((int) x);
    }

    public int getSignum() {
        return Integer.signum(value);
    }

    public Value negate() throws SQLException {
        if (SysProperties.OVERFLOW_EXCEPTIONS) {
            return checkRange(-(long) value);
        }
        return ValueInt.get(-value);
    }

    public Value subtract(Value v) throws SQLException {
        ValueInt other = (ValueInt) v;
        if (SysProperties.OVERFLOW_EXCEPTIONS) {
            return checkRange((long) value - (long) other.value);
        }
        return ValueInt.get(value - other.value);
    }

    public Value multiply(Value v) throws SQLException {
        ValueInt other = (ValueInt) v;
        if (SysProperties.OVERFLOW_EXCEPTIONS) {
            return checkRange((long) value * (long) other.value);
        }
        return ValueInt.get(value * other.value);
    }

    public Value divide(Value v) throws SQLException {
        ValueInt other = (ValueInt) v;
        if (other.value == 0) {
            throw Message.getSQLException(ErrorCode.DIVISION_BY_ZERO_1,
                    getSQL());
        }
        return ValueInt.get(value / other.value);
    }

    public String getSQL() {
        return getString();
    }

    public int getType() {
        return Value.INT;
    }

    public int getInt() {
        return value;
    }

    public long getLong() {
        return value;
    }

    protected int compareSecure(Value o, CompareMode mode) {
        ValueInt v = (ValueInt) o;
        return MathUtils.compare(value, v.value);
    }

    public String getString() {
        return String.valueOf(value);
    }

    public long getPrecision() {
        return PRECISION;
    }

    public int hashCode() {
        return value;
    }

    public Object getObject() {
        return value;
    }

    public void set(PreparedStatement prep, int parameterIndex)
            throws SQLException {
        prep.setInt(parameterIndex, value);
    }

    public int getDisplaySize() {
        return DISPLAY_SIZE;
    }

    public boolean equals(Object other) {
        return other instanceof ValueInt && value == ((ValueInt) other).value;
    }

}
