/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.engine;

/**
 * Constants are fixed values that are used in the whole database code.
 */
public class Constants {

    /**
     * The build id is incremented for each public release.
     */
    public static int BUILD_ID = 128;

    /**
     * The build id of the last stable release.
     */
    public static int BUILD_ID_STABLE = 127;

    /**
     * If H2 is compiled to be included in a product, this should be set to a
     * unique vendor id (to distinguish from official releases). Additionally, a
     * version number should be set to distinguish releases. Example:
     * ACME_SVN1651_BUILD3
     */
    public static String BUILD_VENDOR_AND_VERSION = null;

    /**
     * The build date is updated for each public release.
     */
    public static String BUILD_DATE = "2010-01-30";

    /**
     * The build date is updated for each public release.
     */
    public static String BUILD_DATE_STABLE = "2010-01-15";

    /**
     * The TCP protocol version number 5. This protocol is used by the TCP
     * server and remote JDBC client.
     */
    public static int TCP_PROTOCOL_VERSION_5 = 5;

    /**
     * The TCP protocol version number 6. This protocol is used by the TCP
     * server and remote JDBC client.
     */
    public static int TCP_PROTOCOL_VERSION_6 = 6;

    /**
     * The major version of this database.
     */
    public static int VERSION_MAJOR = 1;

    /**
     * The minor version of this database.
     */
    public static int VERSION_MINOR = 2;

    /**
     * The version number (major.minor) of this database.
     */
    public static double VERSION = VERSION_MAJOR + VERSION_MINOR / 10.;

    /**
     * If empty b-tree pages are allowed. This is supported for backward
     * compatibility.
     */
    public static boolean ALLOW_EMPTY_BTREE_PAGES = true;

    /**
     * Constant meaning both numbers and text is allowed in SQL statements.
     */
    public static int ALLOW_LITERALS_ALL = 2;

    /**
     * Constant meaning no literals are allowed in SQL statements.
     */
    public static int ALLOW_LITERALS_NONE = 0;

    /**
     * Constant meaning only numbers are allowed in SQL statements (but no
     * texts).
     */
    public static int ALLOW_LITERALS_NUMBERS = 1;

    /**
     * Automatically convert large LOB objects to files even if they have been
     * set using setBytes.
     */
    public static boolean AUTO_CONVERT_LOB_TO_FILES = true;

    /**
     * The maximum scale of a BigDecimal value.
     */
    public static int BIG_DECIMAL_SCALE_MAX = 100000;

    /**
     * The minimum number of entries to keep in the cache.
     */
    public static int CACHE_MIN_RECORDS = 16;

    /**
     * The name of the character set used in this database.
     */
    public static String CHARACTER_SET_NAME = "Unicode";

    /**
     * The value of the cluster setting if clustering is disabled.
     */
    public static String CLUSTERING_DISABLED = "''";

    /**
     * The database URL used when calling a function if only the column list
     * should be returned.
     */
    public static String CONN_URL_COLUMNLIST = "jdbc:columnlist:connection";

    /**
     * The database URL used when calling a function if the data should be
     * returned.
     */
    public static String CONN_URL_INTERNAL = "jdbc:default:connection";

    /**
     * The cost is calculated on rowcount + this offset, to avoid using the
     * wrong or no index if the table contains no rows _currently_ (when
     * preparing the statement)
     */
    public static int COST_ROW_OFFSET = 1000;

    /**
     * The default name of the system user. This name is only used as long as
     * there is no administrator user registered.
     */
    public static String DBA_NAME = "DBA";

    /**
     * The number of milliseconds after which to check for a deadlock if locking
     * is not successful.
     */
    public static int DEADLOCK_CHECK = 100;

    /**
     * The default value of the ALLOW_LITERALS setting
     */
    public static int DEFAULT_ALLOW_LITERALS = ALLOW_LITERALS_ALL;

    /**
     * The default data page size.
     */
    public static int DEFAULT_DATA_PAGE_SIZE = 512;

    /**
     * If the HTTP server should allow connections from other computers by
     * default.
     */
    public static boolean DEFAULT_HTTP_ALLOW_OTHERS = false;

    /**
     * The default port number of the HTTP server (for the H2 Console). This
     * value is also in the documentation and in the Server javadoc.
     */
    public static int DEFAULT_HTTP_PORT = 8082;

    /**
     * The default SSL setting for the HTTP server.
     */
    public static boolean DEFAULT_HTTP_SSL = false;

    /**
     * The default value for the maximum log file size.
     */
    public static long DEFAULT_MAX_LOG_SIZE = 32 * 1024 * 1024;

    /**
     * The default maximum length on an in-memory LOB object. Larger objects
     * will be written to a temporary file.
     */
    public static int DEFAULT_MAX_LENGTH_CLIENTSIDE_LOB = 65536;

    /**
     * The default maximum number of rows to be kept in memory in a result set.
     */
    public static int DEFAULT_MAX_MEMORY_ROWS = 10000;

    /**
     * The default port of the TCP server. This port is also used in the
     * documentation.
     */
    public static int DEFAULT_SERVER_PORT = 9092;

    /**
     * The default table type when creating new tables.
     */
    public static int DEFAULT_TABLE_TYPE = 0;

    /**
     * The default delay in milliseconds before the log file is written.
     */
    public static int DEFAULT_WRITE_DELAY = 500;

    /**
     * The name of the JDBC driver.
     */
    public static String DRIVER_NAME = "H2 JDBC Driver";

    /**
     * The password is hashed this many times to slow down dictionary attacks.
     */
    public static int ENCRYPTION_KEY_HASH_ITERATIONS = 1024;

    /**
     * The 'word size' of a file (the minimum allocation size).
     */
    public static int FILE_BLOCK_SIZE = 16;

    /**
     * The maximum number of bytes a file should be expanded in one step.
     */
    public static int FILE_MAX_INCREMENT = 32 * 1024 * 1024;

    /**
     * The minimum file size in bytes.
     */
    public static int FILE_MIN_SIZE = 128 * 1024;

    /**
     * The page size of a file.
     */
    public static int FILE_PAGE_SIZE = 8 * 1024;

    /**
     * The default delay to flush indexes. 0 means indexes are not flushed.
     */
    public static long FLUSH_INDEX_DELAY = 0;

    /**
     * For testing, the lock timeout is smaller than for interactive use cases.
     * This value could be increased to about 5 or 10 seconds.
     */
    public static int INITIAL_LOCK_TIMEOUT = 2000;

    /**
     * The block size for I/O operations.
     */
    public static int IO_BUFFER_SIZE = 4 * 1024;

    /**
     * The block size used to compress data in the LZFOutputStream.
     */
    public static int IO_BUFFER_SIZE_COMPRESS = 128 * 1024;

    /**
     * The lock mode that means no locking is used at all.
     */
    public static int LOCK_MODE_OFF = 0;

    /**
     * The lock mode that means read locks are acquired, but they are released
     * immediately after the statement is executed.
     */
    public static int LOCK_MODE_READ_COMMITTED = 3;

    /**
     * The lock mode that means table level locking is used for reads and
     * writes.
     */
    public static int LOCK_MODE_TABLE = 1;

    /**
     * The lock mode that means table level locking is used for reads and
     * writes. If a table is locked, System.gc is called to close forgotten
     * connections.
     */
    public static int LOCK_MODE_TABLE_GC = 2;

    /**
     * The number of milliseconds to wait between checking the .lock.db file
     * still exists once a database is locked.
     */
    public static int LOCK_SLEEP = 1000;

    /**
     * The file header used for binary files.
     */
    public static String MAGIC_FILE_HEADER = "-- H2 0.5/B --      ".substring(
            0, FILE_BLOCK_SIZE - 1)
            + "\n";

    /**
     * If old text file headers should be supported. This setting can be removed
     * in future versions (required for compatibility with version 1.1.103).
     */
    public static boolean MAGIC_FILE_HEADER_SUPPORT_TEXT = true;

    /**
     * The name of the in-memory management database used by the TCP server to
     * keep the active sessions.
     */
    public static String MANAGEMENT_DB_PREFIX = "management_db_";

    /**
     * The user name of the management database.
     */
    public static String MANAGEMENT_DB_USER = "sa";

    /**
     * The highest possible parameter index.
     */
    public static int MAX_PARAMETER_INDEX = 100000;

    /**
     * The number of bytes in random salt that is used to hash passwords.
     */
    public static int SALT_LEN = 8;

    /**
     * The database URL prefix of this database.
     */
    public static String START_URL = "jdbc:h2:";

    /**
     * The name prefix used for indexes that are not explicitly named.
     */
    public static String PREFIX_INDEX = "INDEX_";

    /**
     * The name prefix used for primary key constraints that are not explicitly
     * named.
     */
    public static String PREFIX_PRIMARY_KEY = "PRIMARY_KEY_";

    /**
     * The product name. This value must stay like that, see
     * http://opensource.atlassian.com/projects/hibernate/browse/HHH-2682
     */
    public static String PRODUCT_NAME = "H2";

    /**
     * Every user belongs to this role.
     */
    public static String PUBLIC_ROLE_NAME = "PUBLIC";

    /**
     * The name of the schema that contains the information schema tables.
     */
    public static String SCHEMA_INFORMATION = "INFORMATION_SCHEMA";

    /**
     * The name of the default schema.
     */
    public static String SCHEMA_MAIN = "PUBLIC";

    /**
     * The default name of the script file if .zip compression is used.
     */
    public static String SCRIPT_SQL = "script.sql";

    /**
     * The default sample size for the ANALYZE statement.
     */
    public static int SELECTIVITY_ANALYZE_SAMPLE_ROWS = 10000;

    /**
     * The default selectivity (used if the selectivity is not calculated).
     */
    public static int SELECTIVITY_DEFAULT = 50;

    /**
     * The number of distinct values to keep in memory when running ANALYZE.
     */
    public static int SELECTIVITY_DISTINCT_COUNT = 10000;

    /**
     * The name of the server properties file.
     */
    public static String SERVER_PROPERTIES_FILE = ".h2.server.properties";

    /**
     * The title of the server properties file.
     */
    public static String SERVER_PROPERTIES_TITLE = "H2 Server Properties";

    /**
     * Queries that take longer than this number of milliseconds are written to
     * the trace file with the level info.
     */
    public static long SLOW_QUERY_LIMIT_MS = 100;

    /**
     * The file name suffix of data files.
     */
    public static String SUFFIX_DATA_FILE = ".data.db";

    /**
     * The file name suffix of page files.
     */
    public static String SUFFIX_PAGE_FILE = ".h2.db";

    /**
     * The file name suffix of all database files.
     */
    public static String SUFFIX_DB_FILE = ".db";

    /**
     * The file name suffix of index files.
     */
    public static String SUFFIX_INDEX_FILE = ".index.db";

    /**
     * The file name suffix of file lock files that are used to make sure a
     * database is open by only one process at any time.
     */
    public static String SUFFIX_LOCK_FILE = ".lock.db";

    /**
     * The file name suffix of large object files.
     */
    public static String SUFFIX_LOB_FILE = ".lob.db";

    /**
     * The suffix of the directory name used if LOB objects are stored in a
     * directory.
     */
    public static String SUFFIX_LOBS_DIRECTORY = ".lobs.db";

    /**
     * The file name suffix of transaction log files.
     */
    public static String SUFFIX_LOG_FILE = ".log.db";

    /**
     * The file name suffix of temporary files.
     */
    public static String SUFFIX_TEMP_FILE = ".temp.db";

    /**
     * The file name suffix of trace files.
     */
    public static String SUFFIX_TRACE_FILE = ".trace.db";

    /**
     * The table name suffix used to create internal temporary tables.
     */
    public static String TEMP_TABLE_PREFIX = "TEMP_TABLE_";

    /**
     * The delay that is to be used if throttle has been enabled.
     */
    public static int THROTTLE_DELAY = 50;

    /**
     * The database URL format in simplified Backus-Naur form.
     */
    public static String URL_FORMAT = START_URL
            + "{ {.|mem:}[name] | [file:]fileName | {tcp|ssl}:[//]server[:port][,server2[:port]]/name }[;key=value...]";

    /**
     * Name of the character encoding format.
     */
    public static String UTF8 = "UTF8";

    /**
     * The major version number of the supported JDBC API.
     */
    public static int VERSION_JDBC_MAJOR = 3;

    /**
     * The minor version number of the supported JDBC API.
     */
    public static int VERSION_JDBC_MINOR = 0;

    /**
     * The maximum time in milliseconds to keep the cost of a view. 10000 means
     * 10 seconds.
     */
    public static int VIEW_COST_CACHE_MAX_AGE = 10000;

    /**
     * The name of the index cache that is used for temporary view (subqueries
     * used as tables).
     */
    public static int VIEW_INDEX_CACHE_SIZE = 64;

    /**
     * Whether searching in Blob values should be supported.
     */
    public static boolean BLOB_SEARCH = false;

    /**
     * The package name of user defined classes.
     */
    public static String USER_PACKAGE = "org.h2.dynamic";

    private Constants() {
        // utility class
    }

    /**
     * Get the version of this product, consisting of major version, minor
     * version, and build id.
     *
     * @return the version number
     */
    public static String getVersion() {
        String version = VERSION_MAJOR + "." + VERSION_MINOR + "." + BUILD_ID;
        if (BUILD_VENDOR_AND_VERSION != null) {
            version += "_" + BUILD_VENDOR_AND_VERSION;
        }
        return version;
    }

    /**
     * Get the last stable version name.
     *
     * @return the version number
     */
    public static Object getVersionStable() {
        return "1.1." + BUILD_ID_STABLE;
    }

    /**
     * Get the complete version number of this database, consisting of the major
     * version, the minor version, the build id, and the build date.
     *
     * @return the complete version
     */
    public static String getFullVersion() {
        return getVersion() + " (" + BUILD_DATE + ")";
    }

}
