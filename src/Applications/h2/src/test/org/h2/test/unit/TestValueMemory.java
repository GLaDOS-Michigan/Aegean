/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import org.h2.constant.SysProperties;
import org.h2.engine.Constants;
import org.h2.message.Message;
import org.h2.message.Trace;
import org.h2.store.DataHandler;
import org.h2.store.FileStore;
import org.h2.test.TestBase;
import org.h2.util.FileUtils;
import org.h2.util.MemoryUtils;
import org.h2.util.SmallLRUCache;
import org.h2.util.TempFileDeleter;
import org.h2.value.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Random;

/**
 * Tests the memory consumption of values. Values can estimate how much memory
 * they occupy, and this tests if this estimation is correct.
 */
public class TestValueMemory extends TestBase implements DataHandler {

    private Random random = new Random(1);
    private SmallLRUCache<String, String[]> lobFileListCache = SmallLRUCache.newInstance(128);

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws SQLException {
        for (int i = 0; i < Value.TYPE_COUNT; i++) {
            testType(i);
        }
    }

    private void testType(int type) throws SQLException {
        System.gc();
        System.gc();
        long first = MemoryUtils.getMemoryUsed();
        ArrayList<Value> list = new ArrayList<Value>();
        long memory = 0;
        for (int i = 0; memory < 1000000; i++) {
            Value v = create(type);
            memory += v.getMemory();
            list.add(v);
        }
        Object[] array = list.toArray();
        IdentityHashMap<Object, Object> map = new IdentityHashMap<Object, Object>();
        for (Object a : array) {
            map.put(a, a);
        }
        int size = map.size();
        map.clear();
        map = null;
        list = null;
        System.gc();
        System.gc();
        long used = MemoryUtils.getMemoryUsed() - first;
        memory /= 1024;
        if (used > memory * 3) {
            fail("Type: " + type + " Used memory: " + used + " calculated: " + memory + " " + array.length + " size: " + size);
        }
    }

    private Value create(int type) throws SQLException {
        switch (type) {
            case Value.NULL:
                return ValueNull.INSTANCE;
            case Value.BOOLEAN:
                return ValueBoolean.get(false);
            case Value.BYTE:
                return ValueByte.get((byte) random.nextInt());
            case Value.SHORT:
                return ValueShort.get((short) random.nextInt());
            case Value.INT:
                return ValueInt.get(random.nextInt());
            case Value.LONG:
                return ValueLong.get(random.nextLong());
            case Value.DECIMAL:
                return ValueDecimal.get(new BigDecimal(random.nextInt()));
            // + "12123344563456345634565234523451312312"
            case Value.DOUBLE:
                return ValueDouble.get(random.nextDouble());
            case Value.FLOAT:
                return ValueFloat.get(random.nextFloat());
            case Value.TIME:
                return ValueTime.get(new java.sql.Time(random.nextLong()));
            case Value.DATE:
                return ValueDate.get(new java.sql.Date(random.nextLong()));
            case Value.TIMESTAMP:
                return ValueTimestamp.get(new java.sql.Timestamp(random.nextLong()));
            case Value.BYTES:
                return ValueBytes.get(randomBytes(random.nextInt(1000)));
            case Value.STRING:
                return ValueString.get(randomString(random.nextInt(100)));
            case Value.STRING_IGNORECASE:
                return ValueStringIgnoreCase.get(randomString(random.nextInt(100)));
            case Value.BLOB: {
                int len = (int) Math.abs(random.nextGaussian() * 10);
                byte[] data = randomBytes(len);
                return ValueLob.createBlob(new ByteArrayInputStream(data), len, this);
            }
            case Value.CLOB: {
                int len = (int) Math.abs(random.nextGaussian() * 10);
                String s = randomString(len);
                return ValueLob.createClob(new StringReader(s), len, this);
            }
            case Value.ARRAY: {
                int len = random.nextInt(20);
                Value[] list = new Value[len];
                for (int i = 0; i < list.length; i++) {
                    list[i] = create(Value.STRING);
                }
                return ValueArray.get(list);
            }
            case Value.RESULT_SET:
                // not supported currently
                return ValueNull.INSTANCE;
            case Value.JAVA_OBJECT:
                return ValueJavaObject.getNoCopy(randomBytes(random.nextInt(100)));
            case Value.UUID:
                return ValueUuid.get(random.nextLong(), random.nextLong());
            case Value.STRING_FIXED:
                return ValueStringFixed.get(randomString(random.nextInt(100)));
            default:
                throw new AssertionError("type=" + type);
        }
    }

    private byte[] randomBytes(int len) {
        byte[] data = new byte[len];
        if (random.nextBoolean()) {
            // don't initialize always (compression)
            random.nextBytes(data);
        }
        return data;
    }

    private String randomString(int len) {
        char[] chars = new char[len];
        if (random.nextBoolean()) {
            // don't initialize always (compression)
            for (int i = 0; i < chars.length; i++) {
                chars[i] = (char) (random.nextGaussian() * 100);
            }
        }
        return new String(chars);
    }

    public int allocateObjectId(boolean needFresh, boolean dataFile) {
        return 0;
    }

    public void checkPowerOff() {
        // nothing to do
    }

    public void checkWritingAllowed() {
        // nothing to do
    }

    public int compareTypeSave(Value a, Value b) {
        return 0;
    }

    public String createTempFile() throws SQLException {
        String name = baseDir + "/valueMemory/data";
        try {
            return FileUtils.createTempFile(name, Constants.SUFFIX_TEMP_FILE, true, false);
        } catch (IOException e) {
            throw Message.convertIOException(e, name);
        }
    }

    public void freeUpDiskSpace() {
        // nothing to do
    }

    public int getChecksum(byte[] data, int s, int e) {
        return 0;
    }

    public String getDatabasePath() {
        return baseDir + "/valueMemory";
    }

    public String getLobCompressionAlgorithm(int type) {
        return "LZF";
    }

    public Object getLobSyncObject() {
        return this;
    }

    public int getMaxLengthInplaceLob() {
        return 100;
    }

    public void handleInvalidChecksum() {
        // nothing to do
    }

    public FileStore openFile(String name, String mode, boolean mustExist) throws SQLException {
        return FileStore.open(this, name, mode);
    }

    public boolean getLobFilesInDirectories() {
        return SysProperties.LOB_FILES_IN_DIRECTORIES;
    }

    public SmallLRUCache<String, String[]> getLobFileListCache() {
        return lobFileListCache;
    }

    public TempFileDeleter getTempFileDeleter() {
        return TempFileDeleter.getInstance();
    }

    public Trace getTrace() {
        return null;
    }

}
