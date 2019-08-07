package BFT.util;


import java.io.File;
import java.io.FilenameFilter;


public class LogFilter implements FilenameFilter {

    String id;

    public LogFilter(String id) {
        this.id = id;
    }

    public boolean accept(File dir, String name) {
        return name.endsWith(id);
    }

}