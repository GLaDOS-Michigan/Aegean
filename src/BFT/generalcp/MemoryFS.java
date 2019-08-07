package BFT.generalcp;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: iodine
 * Date: Apr 25, 2010
 * Time: 5:07:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class MemoryFS {
    private static HashMap<String, byte[]> files = new HashMap<String, byte[]>();

    public static void write(String fileName, byte[] data) {
        //System.out.println("MemoryFS write "+fileName+" len="+data.length);
        files.put(fileName, data);
    }

    public static byte[] read(String fileName) {
        //System.out.println("MemoryFS read "+fileName+" len="+files.get(fileName).length);
        return files.get(fileName);
    }

    public static boolean exists(String fileName) {
        return files.containsKey(fileName);
    }

    public static void delete(String fileName) {
        files.remove(fileName);
    }
}
