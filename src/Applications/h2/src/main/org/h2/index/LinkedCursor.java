/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.index;

import org.h2.engine.Session;
import org.h2.message.Message;
import org.h2.result.Row;
import org.h2.result.SearchRow;
import org.h2.table.Column;
import org.h2.table.TableLink;
import org.h2.value.DataType;
import org.h2.value.Value;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The cursor implementation for the linked index.
 */
public class LinkedCursor implements Cursor {

    private final TableLink tableLink;
    private final PreparedStatement prep;
    private final String sql;
    private final Session session;
    private final ResultSet rs;
    private Row current;

    LinkedCursor(TableLink tableLink, ResultSet rs, Session session, String sql, PreparedStatement prep) {
        this.session = session;
        this.tableLink = tableLink;
        this.rs = rs;
        this.sql = sql;
        this.prep = prep;
    }

    private void closeResultSetAndReusePreparedStatement() throws SQLException {
        rs.close();
        tableLink.reusePreparedStatement(prep, sql);
    }

    public Row get() {
        return current;
    }

    public SearchRow getSearchRow() {
        return current;
    }

    public long getKey() {
        throw Message.throwInternalError();
    }


    public boolean next() throws SQLException {
        boolean result = rs.next();
        if (!result) {
            closeResultSetAndReusePreparedStatement();
            current = null;
            return false;
        }
        current = tableLink.getTemplateRow(true);
        for (int i = 0; i < current.getColumnCount(); i++) {
            Column col = tableLink.getColumn(i);
            Value v = DataType.readValue(session, rs, i + 1, col.getType());
            current.setValue(i, v);
        }
        return true;
    }

    public boolean previous() {
        throw Message.throwInternalError();
    }

}
