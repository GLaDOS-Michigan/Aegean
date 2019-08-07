/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.engine;

import merkle.MerkleTreeInstance;
import org.h2.message.Message;
import org.h2.message.Trace;
import org.h2.table.Table;

import java.sql.SQLException;

/**
 * A persistent database setting.
 */
public class Setting extends DbObjectBase {

    private int intValue;
    protected String stringValue;

    public Setting() {

    }

    public Setting(Database database, int id, String settingName) {
        MerkleTreeInstance.add(this);
        MerkleTreeInstance.update(this);
        initDbObjectBase(database, id, settingName, Trace.SETTING);
    }

    public void setIntValue(int value) {
        MerkleTreeInstance.update(this);
        intValue = value;
    }

    public int getIntValue() {
        return intValue;
    }

    public void setStringValue(String value) {
        MerkleTreeInstance.update(this);
        stringValue = value;
    }

    public String getStringValue() {
        return stringValue;
    }

    public String getCreateSQLForCopy(Table table, String quotedName) {
        throw Message.throwInternalError();
    }

    public String getDropSQL() {
        return null;
    }

    public String getCreateSQL() {
        StringBuilder buff = new StringBuilder("SET ");
        buff.append(getSQL()).append(' ');
        if (stringValue != null) {
            buff.append(stringValue);
        } else {
            buff.append(intValue);
        }
        return buff.toString();
    }

    public int getType() {
        return DbObject.SETTING;
    }

    public void removeChildrenAndResources(Session session) throws SQLException {
        database.removeMeta(session, getId());
        invalidate();
    }

    public void checkRename() throws SQLException {
        throw Message.getUnsupportedException("RENAME");
    }

}
