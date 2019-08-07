/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.table;

import merkle.MerkleTreeInstance;
import org.h2.result.SortOrder;

import java.sql.SQLException;

/**
 * This represents a column item of an index. This is required because some
 * indexes support descending sorted columns.
 */
public class IndexColumn {

    /**
     * The column name.
     */
    public String columnName;

    /**
     * The column, or null if not set.
     */
    public Column column;

    /**
     * The sort type. Ascending (the default) and descending are supported;
     * nulls can be sorted first or last.
     */
    public int sortType = SortOrder.ASCENDING;

    public IndexColumn() {
        MerkleTreeInstance.add(this);
    }

    /**
     * Get the SQL snippet for this index column.
     *
     * @return the SQL snippet
     */
    public String getSQL() {
        StringBuilder buff = new StringBuilder(column.getSQL());
        if ((sortType & SortOrder.DESCENDING) != 0) {
            buff.append(" DESC");
        }
        if ((sortType & SortOrder.NULLS_FIRST) != 0) {
            buff.append(" NULLS FIRST");
        } else if ((sortType & SortOrder.NULLS_LAST) != 0) {
            buff.append(" NULLS LAST");
        }
        return buff.toString();
    }

    /**
     * Create an array of index columns from a list of columns. The default sort
     * type is used.
     *
     * @param columns the column list
     * @return the index column array
     */
    public static IndexColumn[] wrap(Column[] columns) {
        IndexColumn[] list = new IndexColumn[columns.length];
        MerkleTreeInstance.add(list);
        MerkleTreeInstance.update(list);
        for (int i = 0; i < list.length; i++) {
            list[i] = new IndexColumn();
            MerkleTreeInstance.update(list[i]);
            list[i].column = columns[i];
        }
        return list;
    }

    /**
     * Map the columns using the column names and the specified table.
     *
     * @param indexColumns the column list with column names set
     * @param table        the table from where to map the column names to columns
     */
    public static void mapColumns(IndexColumn[] indexColumns, Table table) throws SQLException {
        for (IndexColumn col : indexColumns) {
            MerkleTreeInstance.update(col);
            col.column = table.getColumn(col.columnName);
        }
    }
}
