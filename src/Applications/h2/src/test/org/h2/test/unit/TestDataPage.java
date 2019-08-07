/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import org.h2.constant.SysProperties;
import org.h2.message.Trace;
import org.h2.store.Data;
import org.h2.store.DataHandler;
import org.h2.store.DataPage;
import org.h2.store.FileStore;
import org.h2.test.TestBase;
import org.h2.util.SmallLRUCache;
import org.h2.util.TempFileDeleter;
import org.h2.value.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * Data page tests.
 */
public class TestDataPage extends TestBase implements DataHandler {

    private boolean testPerformance;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws SQLException {
        if (testPerformance) {
            testPerformance();
            System.exit(0);
            return;
        }
        testValues();
        testAll();
    }

    private void testPerformance() {
        Data data = Data.create(null, 1024);
        for (int j = 0; j < 4; j++) {
            long time = System.currentTimeMillis();
            for (int i = 0; i < 100000; i++) {
                data.reset();
                for (int k = 0; k < 30; k++) {
                    data.writeString("Hello World");
                }
            }
            //            for (int i = 0; i < 5000000; i++) {
            //                data.reset();
            //                for (int k = 0; k < 100; k++) {
            //                    data.writeInt(k * k);
            //                }
            //            }
            //            for (int i = 0; i < 200000; i++) {
            //                data.reset();
            //                for (int k = 0; k < 100; k++) {
            //                    data.writeVarInt(k * k);
            //                }
            //            }
            System.out.println("write: " + (System.currentTimeMillis() - time) + " ms");
        }
        for (int j = 0; j < 4; j++) {
            long time = System.currentTimeMillis();
            for (int i = 0; i < 1000000; i++) {
                data.reset();
                for (int k = 0; k < 30; k++) {
                    data.readString();
                }
            }
            //            for (int i = 0; i < 3000000; i++) {
            //                data.reset();
            //                for (int k = 0; k < 100; k++) {
            //                    data.readVarInt();
            //                }
            //            }
            //            for (int i = 0; i < 50000000; i++) {
            //                data.reset();
            //                for (int k = 0; k < 100; k++) {
            //                    data.readInt();
            //                }
            //            }
            System.out.println("read: " + (System.currentTimeMillis() - time) + " ms");
        }
    }

    private void testValues() throws SQLException {
        testValue(ValueNull.INSTANCE);
        testValue(ValueBoolean.get(false));
        testValue(ValueBoolean.get(true));
        for (int i = 0; i < 256; i++) {
            testValue(ValueByte.get((byte) i));
        }
        for (int i = 0; i < 256 * 256; i += 10) {
            testValue(ValueShort.get((short) i));
        }
        for (int i = 0; i < 256 * 256; i += 10) {
            testValue(ValueInt.get(i));
            testValue(ValueInt.get(-i));
            testValue(ValueLong.get(i));
            testValue(ValueLong.get(-i));
        }
        testValue(ValueInt.get(Integer.MAX_VALUE));
        testValue(ValueInt.get(Integer.MIN_VALUE));
        for (long i = 0; i < Integer.MAX_VALUE; i += 10 + i / 4) {
            testValue(ValueInt.get((int) i));
            testValue(ValueInt.get((int) -i));
        }
        testValue(ValueLong.get(Long.MAX_VALUE));
        testValue(ValueLong.get(Long.MIN_VALUE));
        for (long i = 0; i >= 0; i += 10 + i / 4) {
            testValue(ValueLong.get(i));
            testValue(ValueLong.get(-i));
        }
        testValue(ValueDecimal.get(BigDecimal.ZERO));
        testValue(ValueDecimal.get(BigDecimal.ONE));
        testValue(ValueDecimal.get(BigDecimal.TEN));
        testValue(ValueDecimal.get(BigDecimal.ONE.negate()));
        testValue(ValueDecimal.get(BigDecimal.TEN.negate()));
        for (long i = 0; i >= 0; i += 10 + i / 4) {
            testValue(ValueDecimal.get(new BigDecimal(i)));
            testValue(ValueDecimal.get(new BigDecimal(-i)));
            for (int j = 0; j < 200; j += 50) {
                testValue(ValueDecimal.get(new BigDecimal(i).setScale(j)));
                testValue(ValueDecimal.get(new BigDecimal(i * i).setScale(j)));
            }
            testValue(ValueDecimal.get(new BigDecimal(i * i)));
        }
        testValue(ValueDate.get(new Date(System.currentTimeMillis())));
        testValue(ValueDate.get(new Date(0)));
        testValue(ValueTime.get(new Time(System.currentTimeMillis())));
        testValue(ValueTime.get(new Time(0)));
        testValue(ValueTimestamp.get(new Timestamp(System.currentTimeMillis())));
        testValue(ValueTimestamp.get(new Timestamp(0)));
        testValue(ValueJavaObject.getNoCopy(new byte[0]));
        testValue(ValueJavaObject.getNoCopy(new byte[100]));
        for (int i = 0; i < 300; i++) {
            testValue(ValueBytes.getNoCopy(new byte[i]));
        }
        for (int i = 0; i < 65000; i += 10 + i) {
            testValue(ValueBytes.getNoCopy(new byte[i]));
        }
        testValue(ValueUuid.getNewRandom());
        for (int i = 0; i < 100; i++) {
            testValue(ValueString.get(new String(new char[i])));
        }
        for (int i = 0; i < 65000; i += 10 + i) {
            testValue(ValueString.get(new String(new char[i])));
            testValue(ValueStringFixed.get(new String(new char[i])));
            testValue(ValueStringIgnoreCase.get(new String(new char[i])));
        }
        testValue(ValueFloat.get(0f));
        testValue(ValueFloat.get(1f));
        testValue(ValueFloat.get(-1f));
        testValue(ValueDouble.get(0));
        testValue(ValueDouble.get(1));
        testValue(ValueDouble.get(-1));
        for (int i = 0; i < 65000; i += 10 + i) {
            for (double j = 0.1; j < 65000; j += 10 + j) {
                testValue(ValueFloat.get((float) (i / j)));
                testValue(ValueDouble.get(i / j));
                testValue(ValueFloat.get((float) -(i / j)));
                testValue(ValueDouble.get(-(i / j)));
            }
        }
        testValue(ValueArray.get(new Value[0]));
        testValue(ValueArray.get(new Value[]{ValueBoolean.get(true), ValueInt.get(10)}));
    }

    private void testValue(Value v) throws SQLException {
        Data data = Data.create(null, 1024);
        data.checkCapacity((int) v.getPrecision());
        data.writeValue(v);
        data.writeInt(123);
        data.reset();
        Value v2 = data.readValue();
        assertEquals(v.getType(), v2.getType());
        assertTrue(v.compareEqual(v2));
        assertEquals(123, data.readInt());
    }


    private void testAll() throws SQLException {
        DataPage page = DataPage.create(this, 128);

        char[] data = new char[0x10000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (char) i;
        }
        String s = new String(data);
        page.writeString(s);
        int len = page.length();
        assertEquals(len, page.getStringLen(s));
        page.reset();
        assertEquals(s, page.readString());
        page.reset();

        page.writeString("H\u1111!");
        page.writeString("John\tBrack's \"how are you\" M\u1111ller");
        page.writeValue(ValueInt.get(10));
        page.writeValue(ValueString.get("test"));
        page.writeValue(ValueFloat.get(-2.25f));
        page.writeValue(ValueDouble.get(10.40));
        page.writeValue(ValueNull.INSTANCE);
        trace(new String(page.getBytes()));
        page.reset();

        trace(page.readString());
        trace(page.readString());
        trace(page.readValue().getInt());
        trace(page.readValue().getString());
        trace("" + page.readValue().getFloat());
        trace("" + page.readValue().getDouble());
        trace(page.readValue().toString());
        page.reset();

        page.writeInt(0);
        page.writeInt(Integer.MAX_VALUE);
        page.writeInt(Integer.MIN_VALUE);
        page.writeInt(1);
        page.writeInt(-1);
        page.writeInt(1234567890);
        page.writeInt(54321);
        trace(new String(page.getBytes()));
        page.reset();
        trace(page.readInt());
        trace(page.readInt());
        trace(page.readInt());
        trace(page.readInt());
        trace(page.readInt());
        trace(page.readInt());
        trace(page.readInt());

        page = null;
    }

    public String getDatabasePath() {
        return null;
    }

    public FileStore openFile(String name, String mode, boolean mustExist) {
        return null;
    }

    public int getChecksum(byte[] data, int s, int e) {
        return e - s;
    }

    public void checkPowerOff() {
        // nothing to do
    }

    public void checkWritingAllowed() {
        // ok
    }

    public void freeUpDiskSpace() {
        // nothing to do
    }

    public void handleInvalidChecksum() throws SQLException {
        throw new SQLException();
    }

    public int compareTypeSave(Value a, Value b) throws SQLException {
        throw new SQLException();
    }

    public int getMaxLengthInplaceLob() {
        throw new AssertionError();
    }

    public int allocateObjectId(boolean b, boolean c) {
        throw new AssertionError();
    }

    public String createTempFile() throws SQLException {
        throw new SQLException();
    }

    public String getLobCompressionAlgorithm(int type) {
        throw new AssertionError();
    }

    public Object getLobSyncObject() {
        return this;
    }

    public boolean getLobFilesInDirectories() {
        return SysProperties.LOB_FILES_IN_DIRECTORIES;
    }

    public SmallLRUCache<String, String[]> getLobFileListCache() {
        return null;
    }

    public TempFileDeleter getTempFileDeleter() {
        return TempFileDeleter.getInstance();
    }

    public Trace getTrace() {
        return null;
    }

}
