/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.result;

import merkle.MerkleTreeInstance;
import org.h2.store.DataPage;
import org.h2.store.DiskFile;
import org.h2.store.Record;
import org.h2.util.StatementBuilder;
import org.h2.value.Value;

import java.sql.SQLException;

/**
 * Represents a row in a table.
 */
public class Row extends Record implements SearchRow, merkle.MerkleTreeObject, merkle.MerkleTreeCloneable {
    public static final int MEMORY_CALCULATE = -1;
    private long key;
    private Value[] data;
    private int memory;
    private int version;
    //public boolean removed = false;
    //public int referenceCounter = 0;

    private int MTIdentifier = -1;

    public void setMTIdentifier(int identifier) {
        this.MTIdentifier = identifier;
    }

    public int getMTIdentifier() {
        return this.MTIdentifier;
    }

    public Row() {

    }

    public void copyObject(Object dst) {
        super.copyObject(dst);
        Row target = (Row) dst;
        target.key = this.key;
        target.data = this.data;
        target.memory = this.memory;
        target.version = this.version;
        target.inTree = this.inTree;

    }

    public Object cloneObject() {
        Row target = new Row();
        this.copyObject(target);
        return target;
    }

    public Row(boolean addToTree, Value[] data, int memory) {
        if (addToTree) {
            MerkleTreeInstance.add(this);
            inTree = true;
        } else {
            inTree = false;
        }

        this.data = data;
        this.memory = memory;
        /*if(data!=null){
		for(int i=0;i<data.length;i++){
		    if(data[i] instanceof ValueString && ((ValueString)data[i]).getString().equals("Pending")){
			System.err.println("PendingHash="+System.identityHashCode(data[i]));
			Throwable t = new Throwable();
			t.printStackTrace();
		    }
		}
		}*/
    }

    public void setKeyAndVersion(SearchRow row) {
        setKey(row.getKey());
        setVersion(row.getVersion());
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        //if (inTree)
        //{
        MerkleTreeInstance.update(this);
        //}
        this.version = version;
    }

    public long getKey() {
        return key;
    }

    public void setKey(long key) {
        //if (inTree)
        //{
        MerkleTreeInstance.update(this);
        //}
        this.key = key;
    }

    public void setPos(int pos) {
        //if (inTree)
        //{
        MerkleTreeInstance.update(this);
        //}
        super.setPos(pos);
        key = pos;
    }

    public Value getValue(int i) {
        return data[i];
    }

    public void write(DataPage buff) throws SQLException {
        buff.writeInt(data.length);
        for (Value v : data) {
            buff.writeValue(v);
        }
    }

    public int getByteCount(DataPage dummy) throws SQLException {
        int len = data.length;
        int size = DataPage.LENGTH_INT;
        for (int i = 0; i < len; i++) {
            Value v = data[i];
            size += dummy.getValueLen(v);
        }
        return size;
    }

    public void setValue(int i, Value v) {
        //if (inTree)
        //{
        MerkleTreeInstance.update(data);
        //}
        data[i] = v;
	/*	if(data[i] instanceof ValueString && ((ValueString)data[i]).getString().equals("Pending")){
                        System.err.println("PendingHash="+System.identityHashCode(data[i]));
                        Throwable t = new Throwable();
                        t.printStackTrace();
                    }*/

    }

    public boolean isEmpty() {
        return data == null;
    }

    public int getColumnCount() {
        return data.length;
    }

    public int getMemorySize() {
        if (memory != MEMORY_CALCULATE) {
            return blockCount * (DiskFile.BLOCK_SIZE / 8) + memory * 4;
        }
        int m = blockCount * (DiskFile.BLOCK_SIZE / 16);
        for (int i = 0; data != null && i < data.length; i++) {
            m += data[i].getMemory();
        }
        return m;
    }

    public String toString() {
        StatementBuilder buff = new StatementBuilder("( /* key:");
        buff.append(getKey());
        if (version != 0) {
            buff.append(" v:" + version);
        }
        if (isDeleted()) {
            buff.append(" deleted");
        }
        buff.append(" */ ");
        if (data != null) {
            for (Value v : data) {
                buff.appendExceptFirst(", ");
                buff.append(v == null ? "null" : v.getTraceSQL());
            }
        }
        return buff.append(')').toString();
    }

}
