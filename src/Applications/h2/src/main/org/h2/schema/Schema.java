/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.schema;

import merkle.MerkleTreeInstance;
import merkle.wrapper.MTCollectionWrapper;
import merkle.wrapper.MTMapWrapper;
import org.h2.command.ddl.CreateTableData;
import org.h2.constant.ErrorCode;
import org.h2.constant.SysProperties;
import org.h2.constraint.Constraint;
import org.h2.engine.*;
import org.h2.index.Index;
import org.h2.message.Message;
import org.h2.message.Trace;
import org.h2.table.Table;
import org.h2.table.TableData;
import org.h2.table.TableLink;
import org.h2.util.ObjectArray;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;

/**
 * A schema as created by the SQL statement CREATE SCHEMA
 */
public class Schema extends DbObjectBase {

    private User owner;
    private boolean system;

    private MTMapWrapper tablesAndViews = new MTMapWrapper(
            new HashMap<String, Table>(), false, true, false);
    public MTMapWrapper indexes = new MTMapWrapper(
            new HashMap<String, Index>(), false, true, false);
    private MTMapWrapper sequences = new MTMapWrapper(
            new HashMap<String, Sequence>(), false, true, false);
    private MTMapWrapper triggers = new MTMapWrapper(
            new HashMap<String, TriggerObject>(), false, true, false);
    private MTMapWrapper constraints = new MTMapWrapper(
            new HashMap<String, Constraint>(), false, true, false);
    private MTMapWrapper constants = new MTMapWrapper(
            new HashMap<String, Constant>(), false, true, false);

    /**
     * The set of returned unique names that are not yet stored. It is used to
     * avoid returning the same unique name twice when multiple threads
     * concurrently create objects.
     */
    private MTCollectionWrapper temporaryUniqueNames = new MTCollectionWrapper(
            new HashSet<String>(), true, false);

    public Schema() {

    }

    /**
     * Create a new schema object.
     *
     * @param database   the database
     * @param id         the object id
     * @param schemaName the schema name
     * @param owner      the owner of the schema
     * @param system     if this is a system schema (such a schema can not be dropped)
     */
    public Schema(Database database, int id, String schemaName, User owner,
                  boolean system) {
        MerkleTreeInstance.add(this);
        MerkleTreeInstance.add(tablesAndViews);
        MerkleTreeInstance.add(indexes);
        MerkleTreeInstance.add(sequences);
        MerkleTreeInstance.add(triggers);
        MerkleTreeInstance.add(constraints);
        MerkleTreeInstance.add(constants);
        MerkleTreeInstance.add(temporaryUniqueNames);
        initDbObjectBase(database, id, schemaName, Trace.SCHEMA);
        this.owner = owner;
        this.system = system;
    }

    /**
     * Check if this schema can be dropped. System schemas can not be dropped.
     *
     * @return true if it can be dropped
     */
    public boolean canDrop() {
        return !system;
    }

    public String getCreateSQLForCopy(Table table, String quotedName) {
        throw Message.throwInternalError();
    }

    public String getDropSQL() {
        return null;
    }

    public String getCreateSQL() {
        if (system) {
            return null;
        }
        return "CREATE SCHEMA IF NOT EXISTS " + getSQL() + " AUTHORIZATION "
                + owner.getSQL();
    }

    public int getType() {
        return DbObject.SCHEMA;
    }

    public void removeChildrenAndResources(Session session) throws SQLException {
        while (triggers != null && triggers.size() > 0) {
            TriggerObject obj = (TriggerObject) triggers.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (constraints != null && constraints.size() > 0) {
            Constraint obj = (Constraint) constraints.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (tablesAndViews != null && tablesAndViews.size() > 0) {
            Table obj = (Table) tablesAndViews.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (indexes != null && indexes.size() > 0) {
            Index obj = (Index) indexes.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (sequences != null && sequences.size() > 0) {
            Sequence obj = (Sequence) sequences.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (constants != null && constants.size() > 0) {
            Constant obj = (Constant) constants.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        database.removeMeta(session, getId());
        owner = null;
        invalidate();
    }

    public void checkRename() {
        // ok
    }

    /**
     * Get the owner of this schema.
     *
     * @return the owner
     */
    public User getOwner() {
        return owner;
    }

    @SuppressWarnings("unchecked")
    private MTMapWrapper getMap(int type) {
        MTMapWrapper result;
        switch (type) {
            case DbObject.TABLE_OR_VIEW:
                result = tablesAndViews;
                break;
            case DbObject.SEQUENCE:
                result = sequences;
                break;
            case DbObject.INDEX:
                result = indexes;
                break;
            case DbObject.TRIGGER:
                result = triggers;
                break;
            case DbObject.CONSTRAINT:
                result = constraints;
                break;
            case DbObject.CONSTANT:
                result = constants;
                break;
            default:
                throw Message.throwInternalError("type=" + type);
        }
        return result;
    }

    /**
     * Add an object to this schema.
     *
     * @param obj the object to add
     */
    public void add(SchemaObject obj) {
        if (SysProperties.CHECK && obj.getSchema() != this) {
            Message.throwInternalError("wrong schema");
        }
        String name = obj.getName();
        MTMapWrapper map = getMap(obj.getType());
        if (SysProperties.CHECK && map.get(name) != null) {
            Message.throwInternalError("object already exists: " + name);
        }
        map.put(name, obj);
        freeUniqueName(name);
    }

    /**
     * Rename an object.
     *
     * @param obj     the object to rename
     * @param newName the new name
     */
    public void rename(SchemaObject obj, String newName) throws SQLException {
        int type = obj.getType();
        MTMapWrapper map = getMap(type);
        if (SysProperties.CHECK) {
            if (!map.containsKey(obj.getName())) {
                Message.throwInternalError("not found: " + obj.getName());
            }
            if (obj.getName().equals(newName) || map.containsKey(newName)) {
                Message.throwInternalError("object already exists: " + newName);
            }
        }
        obj.checkRename();
        map.remove(obj.getName());
        freeUniqueName(obj.getName());
        obj.rename(newName);
        map.put(newName, obj);
        freeUniqueName(newName);
    }

    /**
     * Try to find a table or view with this name. This method returns null if
     * no object with this name exists. Local temporary tables are also
     * returned.
     *
     * @param session the session
     * @param name    the object name
     * @return the object or null
     */
    public Table findTableOrView(Session session, String name) {
        Table table = (Table) tablesAndViews.get(name);
        if (table == null && session != null) {
            table = session.findLocalTempTable(name);
        }
        return table;
    }

    /**
     * Try to find an index with this name. This method returns null if no
     * object with this name exists.
     *
     * @param session the session
     * @param name    the object name
     * @return the object or null
     */
    public Index findIndex(Session session, String name) {
        Index index = (Index) indexes.get(name);
        if (index == null) {
            index = session.findLocalTempTableIndex(name);
        }
        return index;
    }

    /**
     * Try to find a trigger with this name. This method returns null if no
     * object with this name exists.
     *
     * @param name the object name
     * @return the object or null
     */
    public TriggerObject findTrigger(String name) {
        return (TriggerObject) triggers.get(name);
    }

    /**
     * Try to find a sequence with this name. This method returns null if no
     * object with this name exists.
     *
     * @param sequenceName the object name
     * @return the object or null
     */
    public Sequence findSequence(String sequenceName) {
        return (Sequence) sequences.get(sequenceName);
    }

    /**
     * Try to find a constraint with this name. This method returns null if no
     * object with this name exists.
     *
     * @param session the session
     * @param name    the object name
     * @return the object or null
     */
    public Constraint findConstraint(Session session, String name) {
        Constraint constraint = (Constraint) constraints.get(name);
        if (constraint == null) {
            constraint = session.findLocalTempTableConstraint(name);
        }
        return constraint;
    }

    /**
     * Try to find a user defined constant with this name. This method returns
     * null if no object with this name exists.
     *
     * @param constantName the object name
     * @return the object or null
     */
    public Constant findConstant(String constantName) {
        return (Constant) constants.get(constantName);
    }

    /**
     * Release a unique object name.
     *
     * @param name the object name
     */
    public void freeUniqueName(String name) {
        if (name != null) {
            synchronized (temporaryUniqueNames) {
                temporaryUniqueNames.remove(name);
            }
        }
    }

    private String getUniqueName(DbObject obj,
                                 // HashMap<String, ? extends SchemaObject> map, String prefix)
                                 MTMapWrapper map, String prefix) {
        String hash = Integer.toHexString(obj.getName().hashCode())
                .toUpperCase();
        String name = null;
        synchronized (temporaryUniqueNames) {
            for (int i = 1; i < hash.length(); i++) {
                name = prefix + hash.substring(0, i);
                if (!map.containsKey(name)
                        && !temporaryUniqueNames.contains(name)) {
                    break;
                }
                name = null;
            }
            if (name == null) {
                prefix = prefix + hash + "_";
                for (int i = 0; ; i++) {
                    name = prefix + i;
                    if (!map.containsKey(name)
                            && !temporaryUniqueNames.contains(name)) {
                        break;
                    }
                }
            }
            temporaryUniqueNames.add(name);
        }
        return name;
    }

    /**
     * Create a unique constraint name.
     *
     * @param session the session
     * @param table   the constraint table
     * @return the unique name
     */
    public String getUniqueConstraintName(Session session, Table table) {
        MTMapWrapper tableConstraints;
        if (table.isTemporary() && !table.isGlobalTemporary()) {
            tableConstraints = session.getLocalTempTableConstraints();
        } else {
            tableConstraints = constraints;
        }
        return getUniqueName(table, tableConstraints, "CONSTRAINT_");
    }

    /**
     * Create a unique index name.
     *
     * @param session the session
     * @param table   the indexed table
     * @param prefix  the index name prefix
     * @return the unique name
     */
    public String getUniqueIndexName(Session session, Table table, String prefix) {
        // HashMap<String, Index> tableIndexes;
        MTMapWrapper tableIndexes;
        if (table.isTemporary() && !table.isGlobalTemporary()) {
            tableIndexes = session.getLocalTempTableIndexes();
        } else {
            tableIndexes = indexes;
        }
        return getUniqueName(table, tableIndexes, prefix);
    }

    /**
     * Get the table or view with the given name. Local temporary tables are
     * also returned.
     *
     * @param session the session
     * @param name    the table or view name
     * @return the table or view
     * @throws SQLException if no such object exists
     */
    public Table getTableOrView(Session session, String name)
            throws SQLException {
        Table table = (Table) tablesAndViews.get(name);
        if (table == null && session != null) {
            table = session.findLocalTempTable(name);
        }
        if (table == null) {
            throw Message.getSQLException(ErrorCode.TABLE_OR_VIEW_NOT_FOUND_1,
                    name);
        }
        return table;
    }

    /**
     * Get the index with the given name.
     *
     * @param name the index name
     * @return the index
     * @throws SQLException if no such object exists
     */
    public Index getIndex(String name) throws SQLException {
        Index index = (Index) indexes.get(name);
        if (index == null) {
            throw Message.getSQLException(ErrorCode.INDEX_NOT_FOUND_1, name);
        }
        return index;
    }

    /**
     * Get the constraint with the given name.
     *
     * @param name the constraint name
     * @return the constraint
     * @throws SQLException if no such object exists
     */
    public Constraint getConstraint(String name) throws SQLException {
        Constraint constraint = (Constraint) constraints.get(name);
        if (constraint == null) {
            throw Message.getSQLException(ErrorCode.CONSTRAINT_NOT_FOUND_1,
                    name);
        }
        return constraint;
    }

    /**
     * Get the user defined constant with the given name.
     *
     * @param constantName the constant name
     * @return the constant
     * @throws SQLException if no such object exists
     */
    public Constant getConstant(String constantName) throws SQLException {
        Constant constant = (Constant) constants.get(constantName);
        if (constant == null) {
            throw Message.getSQLException(ErrorCode.CONSTANT_NOT_FOUND_1,
                    constantName);
        }
        return constant;
    }

    /**
     * Get the sequence with the given name.
     *
     * @param sequenceName the sequence name
     * @return the sequence
     * @throws SQLException if no such object exists
     */
    public Sequence getSequence(String sequenceName) throws SQLException {
        Sequence sequence = (Sequence) sequences.get(sequenceName);
        if (sequence == null) {
            throw Message.getSQLException(ErrorCode.SEQUENCE_NOT_FOUND_1,
                    sequenceName);
        }
        return sequence;
    }

    /**
     * Get all objects of the given type.
     *
     * @param type the object type
     * @return a (possible empty) list of all objects
     */
    public ObjectArray<SchemaObject> getAll(int type) {
        MTMapWrapper map = getMap(type);
        return ObjectArray.newInstance(false, map.values());
    }

    /**
     * Get all tables and views.
     *
     * @return a (possible empty) list of all objects
     */
    public ObjectArray<Table> getAllTablesAndViews() {
        return ObjectArray.newInstance(false, tablesAndViews.values());
    }

    /**
     * Remove an object from this schema.
     *
     * @param obj the object to remove
     */
    public void remove(SchemaObject obj) {
        String objName = obj.getName();
        MTMapWrapper map = getMap(obj.getType());
        if (SysProperties.CHECK && !map.containsKey(objName)) {
            Message.throwInternalError("not found: " + objName);
        }
        map.remove(objName);
        freeUniqueName(objName);
    }

    /**
     * Add a table to the schema.
     *
     * @param data the create table information
     * @return the created {@link TableData} object
     */
    public TableData createTable(CreateTableData data) throws SQLException {
        synchronized (database) {
            data.schema = this;
            return new TableData(data);
        }
    }

    /**
     * Add a linked table to the schema.
     *
     * @param id             the object id
     * @param tableName      the table name of the alias
     * @param driver         the driver class name
     * @param url            the database URL
     * @param user           the user name
     * @param password       the password
     * @param originalSchema the schema name of the target table
     * @param originalTable  the table name of the target table
     * @param emitUpdates    if updates should be emitted instead of delete/insert
     * @param force          create the object even if the database can not be accessed
     * @return the {@link TableLink} object
     */
    public TableLink createTableLink(int id, String tableName, String driver,
                                     String url, String user, String password, String originalSchema,
                                     String originalTable, boolean emitUpdates, boolean force)
            throws SQLException {
        synchronized (database) {
            return new TableLink(this, id, tableName, driver, url, user,
                    password, originalSchema, originalTable, emitUpdates, force);
        }
    }

}
