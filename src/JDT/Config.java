package mantis.ftinst;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class Config {
    public static String USAGE = "";
    public static String MainClassName;
    public static String OutputDir;
    public static String[] SrcHomeDir;
    public static Set<String> StartMethodSet;
    public static Set<String> StopMethodSet;
    public static String[] ClassPath;
    public static LinkedList<String> SrcFileList;

    public static boolean UseThreadLocal = false;

    static {
        SrcHomeDir = new String[]{(new File(".")).getAbsolutePath()}; // default source directory
        SrcFileList = new LinkedList<String>();
        StartMethodSet = new HashSet<String>();
        StopMethodSet = new HashSet<String>();
    }

    public static void readConfig(String[] argv) {

        LongOpt[] longopts = new LongOpt[6];
        longopts[0] = new LongOpt("outputdir", LongOpt.REQUIRED_ARGUMENT, null, 'o');
        longopts[1] = new LongOpt("startmethod", LongOpt.REQUIRED_ARGUMENT, null, 's');
        longopts[2] = new LongOpt("stopmethod", LongOpt.REQUIRED_ARGUMENT, null, 'e');
        longopts[3] = new LongOpt("mainclass", LongOpt.REQUIRED_ARGUMENT, null, 'm');
        longopts[4] = new LongOpt("inputdir", LongOpt.REQUIRED_ARGUMENT, null, 0);
        longopts[5] = new LongOpt("classpath", LongOpt.REQUIRED_ARGUMENT, null, 1);

        Getopt g = new Getopt("FeatureInstrumentor", argv, "o:s:e:m:", longopts);
        int c;

        while ((c = g.getopt()) != -1) {

            switch (c) {

                case 0:
                    String rawSrcPathInput = g.getOptarg();
                    SrcHomeDir = rawSrcPathInput.split(File.pathSeparator);
                    for (int i = 0; i < SrcHomeDir.length; i++) {
                        SrcHomeDir[i] = (new File(SrcHomeDir[i])).getAbsolutePath();
                    }
                    System.out.println("Src home directory : " + rawSrcPathInput);
                    break;
                case 1:
                    String rawClassPathInput = g.getOptarg();
                    ClassPath = rawClassPathInput.split(File.pathSeparator);
                    System.out.println("Class path : " + rawClassPathInput);
                    break;
                case 'o':
                    OutputDir = (new File(g.getOptarg())).getAbsolutePath();
                    System.out.println("Output directory : " + OutputDir);
                    break;
                case 's':
                    for (String startMethod : g.getOptarg().split(":")) {
                        StartMethodSet.add(startMethod);
                    }
                    System.out.println("Start method : " + Arrays.toString(StartMethodSet.toArray()));
                    break;
                case 'e':
                    for (String startMethod : g.getOptarg().split(":")) {
                        StopMethodSet.add(startMethod);
                    }
                    System.out.println("End method : " + Arrays.toString(StopMethodSet.toArray()));
                    break;
                case 'm':
                    MainClassName = g.getOptarg();
                    System.out.println("Main class : " + MainClassName);
                    break;

                default:
                    System.out.println("Unknown argument : " + (char) c);
                    System.exit(-1);
                    break;

            }
        }

        for (int i = g.getOptind(); i < argv.length; i++) {
            File f = new File(argv[i]);
            if (f.exists()) {
                SrcFileList.add(f.getAbsolutePath());
            } else {
                System.err.println("Source file " + argv[i] + " does NOT exist!");
                System.exit(-1);
            }
        }


    }


}
