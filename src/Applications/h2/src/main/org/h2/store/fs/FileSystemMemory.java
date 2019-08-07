/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store.fs;

import org.h2.message.Message;
import org.h2.util.IOUtils;
import org.h2.util.ObjectArray;
import org.h2.util.RandomUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This file system keeps files fully in memory.
 * There is an option to compress file blocks to safe memory.
 */
public class FileSystemMemory extends FileSystem {

    /**
     * The prefix used for an in-memory file system.
     */
    public static final String PREFIX = "memFS:";

    /**
     * The prefix used for a compressed in-memory file system.
     */
    public static final String PREFIX_LZF = "memLZF:";

    private static final FileSystemMemory INSTANCE = new FileSystemMemory();
    private static final TreeMap<String, FileObjectMemory> MEMORY_FILES = new TreeMap<String, FileObjectMemory>();

    private FileSystemMemory() {
        // don't allow construction
    }

    public static FileSystemMemory getInstance() {
        return INSTANCE;
    }

    public long length(String fileName) {
        return getMemoryFile(fileName).length();
    }

    public void rename(String oldName, String newName) {
        oldName = normalize(oldName);
        newName = normalize(newName);
        synchronized (MEMORY_FILES) {
            FileObjectMemory f = getMemoryFile(oldName);
            f.setName(newName);
            MEMORY_FILES.remove(oldName);
            MEMORY_FILES.put(newName, f);
        }
    }

    public boolean createNewFile(String fileName) {
        synchronized (MEMORY_FILES) {
            if (exists(fileName)) {
                return false;
            }
            getMemoryFile(fileName);
        }
        return true;
    }

    public boolean exists(String fileName) {
        fileName = normalize(fileName);
        synchronized (MEMORY_FILES) {
            return MEMORY_FILES.get(fileName) != null;
        }
    }

    public void delete(String fileName) {
        fileName = normalize(fileName);
        synchronized (MEMORY_FILES) {
            MEMORY_FILES.remove(fileName);
        }
    }

    public boolean tryDelete(String fileName) {
        delete(fileName);
        return true;
    }

    public String createTempFile(String name, String suffix, boolean deleteOnExit, boolean inTempDir) {
        name += ".";
        synchronized (MEMORY_FILES) {
            for (int i = 0; ; i++) {
                String n = name + (RandomUtils.getSecureLong() >>> 1) + suffix;
                if (!exists(n)) {
                    getMemoryFile(n);
                    return n;
                }
            }
        }
    }

    public String[] listFiles(String path) {
        ObjectArray<String> list = ObjectArray.newInstance(false);
        synchronized (MEMORY_FILES) {
            for (String name : MEMORY_FILES.tailMap(path).keySet()) {
                if (name.startsWith(path)) {
                    list.add(name);
                } else {
                    break;
                }
            }
            String[] array = new String[list.size()];
            list.toArray(array);
            return array;
        }
    }

    public void deleteRecursive(String fileName, boolean tryOnly) {
        fileName = normalize(fileName);
        synchronized (MEMORY_FILES) {
            Iterator<String> it = MEMORY_FILES.tailMap(fileName).keySet().iterator();
            while (it.hasNext()) {
                String name = it.next();
                if (name.startsWith(fileName)) {
                    it.remove();
                } else {
                    break;
                }
            }
        }
    }

    public boolean isReadOnly(String fileName) {
        return false;
    }

    public String normalize(String fileName) {
        fileName = fileName.replace('\\', '/');
        int idx = fileName.indexOf(":/");
        if (idx > 0) {
            fileName = fileName.substring(0, idx + 1) + fileName.substring(idx + 2);
        }
        return fileName;
    }

    public String getParent(String fileName) {
        fileName = normalize(fileName);
        int idx = fileName.lastIndexOf('/');
        if (idx < 0) {
            idx = fileName.indexOf(':') + 1;
        }
        return fileName.substring(0, idx);
    }

    public boolean isDirectory(String fileName) {
        // TODO in memory file system currently
        // does not really support directories
        return false;
    }

    public boolean isAbsolute(String fileName) {
        // TODO relative files are not supported
        return true;
    }

    public String getAbsolutePath(String fileName) {
        // TODO relative files are not supported
        return normalize(fileName);
    }

    public long getLastModified(String fileName) {
        return getMemoryFile(fileName).getLastModified();
    }

    public boolean canWrite(String fileName) {
        return true;
    }

    public void copy(String original, String copy) throws SQLException {
        try {
            OutputStream out = openFileOutputStream(copy, false);
            InputStream in = openFileInputStream(original);
            IOUtils.copyAndClose(in, out);
        } catch (IOException e) {
            throw Message.convertIOException(e, "Can not copy " + original + " to " + copy);
        }
    }

    public void createDirs(String fileName) {
        // TODO directories are not really supported
    }

    public String getFileName(String name) {
        int idx = Math.max(name.indexOf(':'), name.lastIndexOf('/'));
        return idx < 0 ? name : name.substring(idx + 1);
    }

    public boolean fileStartsWith(String fileName, String prefix) {
        fileName = normalize(fileName);
        prefix = normalize(prefix);
        return fileName.startsWith(prefix);
    }

    public OutputStream openFileOutputStream(String fileName, boolean append) throws SQLException {
        try {
            FileObjectMemory obj = getMemoryFile(fileName);
            obj.seek(0);
            return new FileObjectOutputStream(obj, append);
        } catch (IOException e) {
            throw Message.convertIOException(e, fileName);
        }
    }

    public InputStream openFileInputStream(String fileName) {
        FileObjectMemory obj = getMemoryFile(fileName);
        obj.seek(0);
        return new FileObjectInputStream(obj);
    }

    public FileObject openFileObject(String fileName, String mode) {
        FileObjectMemory obj = getMemoryFile(fileName);
        obj.seek(0);
        return obj;
    }

    private FileObjectMemory getMemoryFile(String fileName) {
        fileName = normalize(fileName);
        synchronized (MEMORY_FILES) {
            FileObjectMemory m = MEMORY_FILES.get(fileName);
            if (m == null) {
                boolean compress = fileName.startsWith(PREFIX_LZF);
                m = new FileObjectMemory(fileName, compress);
                MEMORY_FILES.put(fileName, m);
            }
            return m;
        }
    }

    protected boolean accepts(String fileName) {
        return fileName.startsWith(PREFIX) || fileName.startsWith(PREFIX_LZF);
    }

}
