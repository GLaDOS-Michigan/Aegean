/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import org.h2.test.TestBase;
import org.h2.tools.DeleteDbFiles;
import org.h2.util.IOUtils;
import org.h2.util.StringUtils;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Tests the sample apps.
 */
public class TestSampleApps extends TestBase {

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws Exception {
        deleteDb("optimizations");
        InputStream in = getClass().getClassLoader().getResourceAsStream("org/h2/samples/optimizations.sql");
        new File(baseDir).mkdirs();
        FileOutputStream out = new FileOutputStream(baseDir + "/optimizations.sql");
        IOUtils.copyAndClose(in, out);
        String url = "jdbc:h2:" + baseDir + "/optimizations";
        testApp("", org.h2.tools.RunScript.class, "-url", url, "-user", "sa", "-password", "sa", "-script",
                baseDir + "/optimizations.sql", "-checkResults");
        deleteDb("optimizations");
        testApp("Compacting...\nDone.", org.h2.samples.Compact.class);
        testApp("NAME: Bob Meier\n" + "EMAIL: bob.meier@abcde.abc\n"
                        + "PHONE: +41123456789\n\n" + "NAME: John Jones\n" + "EMAIL: john.jones@abcde.abc\n"
                        + "PHONE: +41976543210\n",
                org.h2.samples.CsvSample.class);
        testApp("2 is prime\n3 is prime\n5 is prime\n7 is prime\n11 is prime\n13 is prime\n17 is prime\n19 is prime\n30\n20\n0/0\n0/1\n1/0\n1/1",
                org.h2.samples.Function.class);
        // Not compatible with PostgreSQL JDBC driver (throws a NullPointerException)
        //testApp(org.h2.samples.SecurePassword.class, null, "Joe");
        // TODO test ShowProgress (percent numbers are hardware specific)
        // TODO test ShutdownServer (server needs to be started in a separate
        // process)
        testApp("The sum is 20.00", org.h2.samples.TriggerSample.class);
        testApp("Hello: 1\nWorld: 2", org.h2.samples.TriggerPassData.class);

        // tools
        testApp("Allows changing the database file encryption password or algorithm*",
                org.h2.tools.ChangeFileEncryption.class, "-help");
        testApp("Allows changing the database file encryption password or algorithm*",
                org.h2.tools.ChangeFileEncryption.class);
        testApp("Deletes all files belonging to a database.*",
                org.h2.tools.DeleteDbFiles.class, "-help");
    }

    private void testApp(String expected, Class<?> clazz, String... args) throws Exception {
        DeleteDbFiles.execute("data", "test", true);
        Method m = clazz.getMethod("main", String[].class);
        PrintStream oldOut = System.out, oldErr = System.err;
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buff, false, "UTF-8");
        System.setOut(out);
        System.setErr(out);
        try {
            m.invoke(null, new Object[]{args});
        } catch (InvocationTargetException e) {
            TestBase.logError("error", e.getTargetException());
        } catch (Throwable e) {
            TestBase.logError("error", e);
        }
        out.flush();
        System.setOut(oldOut);
        System.setErr(oldErr);
        String s = new String(buff.toByteArray(), "UTF-8");
        s = StringUtils.replaceAll(s, "\r\n", "\n");
        s = s.trim();
        expected = expected.trim();
        if (expected.endsWith("*")) {
            expected = expected.substring(0, expected.length() - 1);
            if (!s.startsWith(expected)) {
                assertEquals(expected.trim(), s.trim());
            }
        } else {
            assertEquals(expected.trim(), s.trim());
        }
    }
}
