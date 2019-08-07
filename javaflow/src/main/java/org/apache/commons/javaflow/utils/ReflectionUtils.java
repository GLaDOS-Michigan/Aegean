/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.javaflow.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class ReflectionUtils {

    private final static Log log = LogFactory.getLog(ReflectionUtils.class);

    public interface Matcher {
        boolean matches(final String pName);
    }

    public interface Indexer {
        void put(final Map<String, Object> pMap, final String pKey, final Object pObject);
    }

    private static Indexer defaultIndexer = new DefaultIndexer();
    private static Matcher defaultMatcher = new DefaultMatcher();

    public static class DefaultMatcher implements Matcher {
        public boolean matches(final String pName) {
            return true;
        }
    }

    public static class DefaultIndexer implements Indexer {
        public void put(final Map<String, Object> pMap, final String pKey, final Object pObject) {
            pMap.put(pKey, pObject);
        }
    }

    public static Map<String, Object> discoverFields(
            final Class<?> pClazz,
            final Matcher pMatcher
            ) {

        return discoverFields(pClazz, pMatcher, defaultIndexer);
    }

    public static Map<String, Object> discoverFields(
            final Class<?> pClazz
            ) {

        return discoverFields(pClazz, defaultMatcher, defaultIndexer);
    }

    public static Map<String, Object> discoverFields(
            final Class<?> pClazz,
            final Matcher pMatcher,
            final Indexer pIndexer
            ) {

        log.debug("discovering fields on " + pClazz.getName());

        final Map<String, Object> result = new HashMap<String, Object>();

        Class<?> current = pClazz;
        do {
            for (Field field : current.getDeclaredFields()) {
                final String fname = field.getName();
                if (pMatcher.matches(fname)) {
                    pIndexer.put(result, fname, field);

                    log.debug("discovered field " + fname + " -> " + field);
                }
            }
            current = current.getSuperclass();
        } while(current != null);

        return result;
    }


    public static Map<String, Object> discoverMethods(
            final Class<?> pClazz,
            final Matcher pMatcher
            ) {

        return discoverMethods(pClazz, pMatcher, defaultIndexer);
    }

    public static Map<String, Object> discoverMethods(
            final Class<?> pClazz
            ) {

        return discoverMethods(pClazz, defaultMatcher, defaultIndexer);
    }

    public static Map<String, Object> discoverMethods(
            final Class<?> pClazz,
            final Matcher pMatcher,
            final Indexer pIndexer
            ) {

        log.debug("discovering methods on " + pClazz.getName());

        final Map<String, Object> result = new HashMap<String, Object>();

        Class<?> current = pClazz;
        do {
            for (Method method : current.getDeclaredMethods()) {
                final String mname = method.getName();
                if (pMatcher.matches(mname)) {
                    pIndexer.put(result, mname, method);

                    log.debug("discovered method " + mname + " -> " + method);
                }
            }
            current = current.getSuperclass();
        } while(current != null);

        return result;
    }

    public static Object cast(Object o) throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream(buffer);
        oos.writeObject(o);
        oos.flush();
        final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        return ois.readObject();
      }

    public static String getClassName(final Object o) {
        if (o == null) {
            return "unknown";
        }

        return o.getClass().getName() + "@" + o.hashCode();
    }

    public static String getClassName1(final Object o) {
        if (o == null) {
            return "unknown";
        }

        return o.getClass().getName();
    }

    public static String getClassLoaderName(final Object o) {
        if (o == null) {
            return "unknown";
        }

        return getClassName(o.getClass().getClassLoader());
    }

    public static String getClassLoaderName1(final Object o) {
        if (o == null) {
            return "unknown";
        }

        return getClassName1(o.getClass().getClassLoader());
    }
}
