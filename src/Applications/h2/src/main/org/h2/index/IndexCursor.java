/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.index;

import org.h2.engine.Session;
import org.h2.expression.Comparison;
import org.h2.message.Message;
import org.h2.result.ResultInterface;
import org.h2.result.Row;
import org.h2.result.SearchRow;
import org.h2.result.SortOrder;
import org.h2.table.Column;
import org.h2.table.IndexColumn;
import org.h2.table.Table;
import org.h2.util.ObjectArray;
import org.h2.value.Value;
import org.h2.value.ValueNull;

import java.sql.SQLException;
import java.util.HashSet;

/**
 * The filter used to walk through an index. This class supports IN(..) and
 * IN(SELECT ...) optimizations.
 */
public class IndexCursor implements Cursor {
    private Session session;
    private Index index;
    private Table table;
    private IndexColumn[] indexColumns;
    private boolean alwaysFalse;

    private SearchRow start, end;
    private Cursor cursor;
    private Column inColumn;
    private int inListIndex;
    private Value[] inList;
    private ResultInterface inResult;
    private HashSet<Value> inResultTested;

    public IndexCursor() {

    }

    public void setIndex(Index index) {
        this.index = index;
        this.table = index.getTable();
        Column[] columns = table.getColumns();
        indexColumns = new IndexColumn[columns.length];
        IndexColumn[] idxCols = index.getIndexColumns();
        if (idxCols != null) {
            for (int i = 0; i < columns.length; i++) {
                int idx = index.getColumnIndex(columns[i]);
                if (idx == 0) {
                    indexColumns[i] = idxCols[idx];
                }
            }
        }
    }

    /**
     * Re-evaluate the start and end values of the index search for rows.
     *
     * @param s               the session
     * @param indexConditions the index conditions
     */
    public void find(Session s, ObjectArray<IndexCondition> indexConditions)
            throws SQLException {
        this.session = s;
        alwaysFalse = false;
        SearchRow previousStart = start;
        SearchRow previousEnd = end;
        start = end = null;
        inList = null;
        inResult = null;
        inResultTested = new HashSet<Value>();
        for (IndexCondition condition : indexConditions) {
            if (condition.isAlwaysFalse()) {
                alwaysFalse = true;
                break;
            }
            Column column = condition.getColumn();
            if (condition.getCompareType() == Comparison.IN_LIST) {
                if (start == null && end == null) {
                    this.inColumn = column;
                    inList = condition.getCurrentValueList(s);
                    inListIndex = 0;
                }
            } else if (condition.getCompareType() == Comparison.IN_QUERY) {
                if (start == null && end == null) {
                    this.inColumn = column;
                    inResult = condition.getCurrentResult(s);
                }
            } else {
                Value v = column.convert(condition.getCurrentValue(s));
                boolean isStart = condition.isStart();
                boolean isEnd = condition.isEnd();
                int id = column.getColumnId();
                IndexColumn idxCol = indexColumns[id];
                if (idxCol != null
                        && (idxCol.sortType & SortOrder.DESCENDING) != 0) {
                    // if the index column is sorted the other way, we swap end
                    // and start
                    // NULLS_FIRST / NULLS_LAST is not a problem, as nulls never
                    // match anyway
                    boolean temp = isStart;
                    isStart = isEnd;
                    isEnd = temp;
                }
                if (isStart) {
                    start = getSearchRow(start, id, v, true);
                    if (previousStart != null && previousStart != start) {
                        /*((Row) previousStart).referenceCounter--;
						if (((Row) previousStart).referenceCounter == 0)
						{
							MerkleTreeInstance
									.remove(((Row) previousStart).data);
							((Row)previousStart).inTree = false;
							MerkleTreeInstance.remove(previousStart);
						}*/
                    }
                }
                if (isEnd) {
                    end = getSearchRow(end, id, v, false);
					/*if (previousEnd != null && previousEnd != end)
					{
						((Row) previousEnd).referenceCounter--;
						if (((Row) previousEnd).referenceCounter == 0)
						{
							MerkleTreeInstance.remove(((Row) previousEnd).data);
							((Row)previousEnd).inTree = false;
							MerkleTreeInstance.remove(previousEnd);
						}
					}*/
                }
                if (isStart || isEnd) {
                    // an X=? condition will produce less rows than
                    // an X IN(..) condition
                    inColumn = null;
                    inList = null;
                    inResult = null;
                }
                if (isStart && isEnd) {
                    if (v == ValueNull.INSTANCE) {
                        // join on a column=NULL is always false
                        alwaysFalse = true;
                    }
                }
            }
        }

        if (inColumn != null) {
            return;
        }
        if (!alwaysFalse) {
            cursor = index.find(s, start, end);
        }
    }

    private SearchRow getSearchRow(SearchRow row, int id, Value v, boolean max)
            throws SQLException {
        if (row == null) {
            row = table.getTemplateRow(false);
        } else {
            v = getMax(row.getValue(id), v, max);
        }
        row.setValue(id, v);
        return row;
    }

    private Value getMax(Value a, Value b, boolean bigger) throws SQLException {
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        }
        int comp = a.compareTo(b, table.getDatabase().getCompareMode());
        if (!bigger) {
            comp = -comp;
        }
        return comp > 0 ? a : b;
    }

    /**
     * Check if the result is empty for sure.
     *
     * @return true if it is
     */
    public boolean isAlwaysFalse() {
        return alwaysFalse;
    }

    public Row get() throws SQLException {
        return cursor.get();
    }

    public long getKey() {
        return cursor.getKey();
    }

    public SearchRow getSearchRow() throws SQLException {
        return cursor.getSearchRow();
    }

    public boolean next() throws SQLException {
        while (true) {
            if (cursor == null) {
                nextCursor();
                if (cursor == null) {
                    return false;
                }
            }
            if (cursor.next()) {
                return true;
            }
            cursor = null;
        }
    }

    private void nextCursor() throws SQLException {
        if (inList != null) {
            if (inListIndex < inList.length) {
                Value v = inList[inListIndex++];
                find(v);
            }
        } else if (inResult != null) {
            while (inResult.next()) {
                Value v = inResult.currentRow()[0];
                v = inColumn.convert(v);
                if (inResultTested.add(v)) {
                    find(v);
                    break;
                }
            }
        }
    }

    private void find(Value v) throws SQLException {
        v = inColumn.convert(v);
        int id = inColumn.getColumnId();
        if (start == null) {
            start = table.getTemplateRow(false);
        }
        start.setValue(id, v);
        cursor = index.find(session, start, start);
    }

    public boolean previous() {
        throw Message.throwInternalError();
    }

}
