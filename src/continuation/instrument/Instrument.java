package continuation.instrument;

import org.apache.commons.javaflow.bytecode.transformation.ResourceTransformer;
import org.apache.commons.javaflow.bytecode.transformation.asm.AsmClassTransformer;
import org.apache.commons.javaflow.utils.RewritingUtils;

import java.io.File;

/**
 * Created by IntelliJ IDEA. User: iodine Date: Nov 17, 2012 Time: 2:01:51 PM To
 * change this template use File | Settings | File Templates.
 */
public class Instrument {

    private static String[] fileNames = null;
    private static String srcDir = null;
    private static String dstDir = null;

    private static void parseArguments(String args[]) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-src=")) {
                srcDir = args[i].substring("-src=".length());
            } else if (args[i].startsWith("-dst=")) {
                dstDir = args[i].substring("-dst=".length());
            } else if (args[i].startsWith("-classFiles=")) {
                fileNames = args[i].substring("-classFiles=".length()).split(":");
            } else {
                System.out.println("Usage: Instrument -src=<srcDir> -dst=<dstDir> -classFiles=<class files to instrument>");
                System.exit(-1);
            }
        }

        if (fileNames == null || srcDir == null || dstDir == null) {
            System.out.println("Usage: Instrument -src=<srcDir> -dst=<dstDir> -classFiles=<class files to instrument>");
            System.exit(-1);
        }
    }

    public static void main(String args[]) {
        ResourceTransformer transformer = new AsmClassTransformer();
        parseArguments(args);

        try {
            for (String fileName : fileNames) {
                try {
                    System.out.println("Instrumenting " + fileName);
                    final File source = new File(srcDir, fileName);
                    final File destination = new File(dstDir, fileName);
                    if (!destination.getParentFile().exists()) {
                        destination.getParentFile().mkdirs();
                    }
                    if (fileName.endsWith(".class")) {
                        RewritingUtils.rewriteClassFile(source, transformer, destination);
                    }
                } catch (RuntimeException re) {
                    if (!re.getMessage().endsWith("has already been instrumented")) {
                        throw re;
                    } else {
                        System.out.println(re.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
