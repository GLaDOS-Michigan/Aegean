package JDT;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import java.io.*;
import java.util.*;

public class ParseAST {
    private static String[] srcHomeDir;
    private static String outputDir;
    protected static Hashtable<String, ArrayList<String>> fields;
    private static ArrayList<String> dontwork = new ArrayList<String>();

    public ParseAST() {
        fields = new Hashtable<String, ArrayList<String>>();

        srcHomeDir = new String[1];
        srcHomeDir[0] = "/Users/manos/ut/research/OSDI10/src/JDT";

        outputDir = "/Users/manos/ut/research/OSDI10/src/JDT/output";
    }

    // Map from class name to AST root node 
    public HashMap<String, CompilationUnit> astRootMap = new HashMap<String, CompilationUnit>();

    private void parse(String[] srcFiles, String[] sourcePathEntries, String[] classPathEntries) {
        System.out.println("SRC files : " + Arrays.toString(srcFiles));
        System.out.println("CLASS PATH Entries : " + Arrays.toString(classPathEntries));
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setEnvironment(classPathEntries, sourcePathEntries, null, true);
        parser.setBindingsRecovery(true);
        HashMap map = new HashMap();
        map.put("org.eclipse.jdt.core.compiler.compliance", "1.5");
        map.put("org.eclipse.jdt.core.compiler.source", "1.5");
        parser.setCompilerOptions(map);

        parser.createASTs(srcFiles, null, new String[0],
                new FileASTRequestor() {
                    public void acceptAST(String sourceFilePath, CompilationUnit ast) {
                        //System.out.println("FILE:"+sourceFilePath+" FILE LENGTH FROM AST="+ast.getLength());
                        String k = fullSrcPathToClassName(sourceFilePath);
                        //String k = sourceFilePath;
                        ast.recordModifications();

                        astRootMap.put(k, ast);
                    }
                }, null);


        if (astRootMap.size() != srcFiles.length) {
            System.out.println(astRootMap.size() + " != " + srcFiles.length);
            System.err.println("Not all files are parsed successfuly.");
            dontwork.add(srcFiles[0]);
            System.exit(-1);
        }

    }

    private static String classNameToFullSrcPath(String className) {

        for (String srcDir : srcHomeDir) {
            String filename =
                    srcDir + File.separator + className.replace('.', File.separatorChar) + ".java";
            System.out.println("Filename: " + filename);
            File f = new File(filename);
            if (f.exists()) return filename;
        }

        return null;
//        return Config.SrcHomeDir + File.separator + className.replace('.', File.separatorChar ) + ".java";
    }

    private static String classNameToFullDstPath(String className) {
        return outputDir + File.separator + className.replace('.', File.separatorChar) + ".java";
    }

    private static String fullSrcPathToClassName(String fullPath) {
        System.out.println("fullPath: " + fullPath);
        String srchomedir = null;
        for (String homeDir : srcHomeDir) {
            if (fullPath.startsWith(homeDir)) {
                srchomedir = homeDir;
                break;
            }
        }

        if (srchomedir == null) {
            System.err.println("Wrong fullpath : " + fullPath);
            (new Throwable()).printStackTrace();
            System.exit(-1);
            return null;
        }

        int idx = fullPath.indexOf(srchomedir);
        String path = fullPath.substring(idx + srchomedir.length() + 1);
        String tmp = path.substring(0, path.lastIndexOf(".java"));
        return tmp.replace(File.separator, ".");
    }

    /**
     * Write current ASTes to the output files
     */
    public void writeToFiles() {
        for (String className : astRootMap.keySet()) {
            writeToFile(className);
        }
    }

    private void writeToFile(String className) {

        CompilationUnit cu = astRootMap.get(className);
        assert cu != null;
        IDocument doc = null;

        // Read the original source code
        //if(className.startsWith(MANTIS_PACKAGE_NAME)){
        //	doc = new Document(mantisClassInitialCodeMap.get(className));
        //} else {
        String src;
        src = ParseAST.classNameToFullSrcPath(className);
        System.out.println("Class name: " + className + " Full source path: " + src);
        try {
            FileReader fr = new FileReader(src);
            char[] buf = new char[cu.getLength()];
            fr.read(buf);
            doc = new Document(new String(buf));
        } catch (FileNotFoundException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        //}

        // Apply the modifications we have made
        TextEdit textedit = cu.rewrite(doc, null);
        try {
            textedit.apply(doc);
        } catch (MalformedTreeException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (BadLocationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // Write it to the output directory
        File outdir = new File(outputDir);
        if (!outdir.exists()) {
            outdir.mkdirs();
        }
        File outfile = new File(ParseAST.classNameToFullDstPath(className));
        System.out.println("Writing to " + outfile.getName());
        // delete the existing file
        if (outfile.exists() && !outfile.delete()) {
            System.err.println(outfile.getAbsolutePath() + " cannot be deleted.");
            System.exit(-1);
        }
        try {
            outfile.getParentFile().mkdirs();
            outfile.createNewFile();
            FileWriter fw = new FileWriter(outfile);
            fw.write(doc.get());
            fw.flush();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to write " + outfile.getAbsolutePath());
            System.exit(-1);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java JDT.ParseAST <packageDir> <file1> [<file2>] [...]");
            System.exit(-1);
        }
        String packageDir = args[0];
        System.out.println("Package dir: " + packageDir);
        int numFiles = args.length - 1;
        System.out.println("Have " + numFiles + " files");
        for (int i = 0; i < numFiles; i++) {
            System.out.println("File " + i + ": " + args[i + 1]);
        }

        //if(true) System.exit(-1);

        String[] inputFiles = new String[numFiles];
        String[] sourcePathEntries = new String[2];
        String[] classPathEntries = new String[7];
        String parentDir = (new File(packageDir).getParentFile()).getAbsolutePath();
        //srcFiles[0] = "/Users/manos/ut/research/OSDI10/src/JDT/SimpleTest.java";
        for (int i = 0; i < numFiles; i++) {
            inputFiles[i] = args[i + 1];
        }
        sourcePathEntries[0] = packageDir;
        sourcePathEntries[1] = parentDir;
        //"/Users/manos/ut/research/OSDI10/src/JDT/sampleDir";

        classPathEntries[0] = packageDir;
        classPathEntries[1] = "/Users/manos/ut/research/OSDI10/src/Applications/h2/ext/lucene-core-2.2.0.jar";
        classPathEntries[2] = "/Users/manos/ut/research/OSDI10/src/Applications/h2/ext/org.osgi.core-1.2.0.jar";
        classPathEntries[3] = "/Users/manos/ut/research/OSDI10/src/Applications/h2/ext/servlet-api-2.4.jar";
        classPathEntries[4] = "/Users/manos/ut/research/OSDI10/src/Applications/h2/ext/slf4j-api-1.5.0.jar";
        classPathEntries[5] = "/Users/manos/ut/research/OSDI10/src/Applications/h2/ext/bft.jar";
        classPathEntries[6] = parentDir;//"/Users/manos/ut/research/OSDI10/src/JDT/sampleDir";

        String[] srcFiles = new String[1];

        for (int i = 0; i < numFiles; i++) {
            srcFiles[0] = inputFiles[i];
            System.out.println("srcFiles[0] = " + srcFiles[0]);
            ParseAST ast = new ParseAST();
            ast.parse(srcFiles, sourcePathEntries, classPathEntries);

            Object[] keys = ast.astRootMap.keySet().toArray();
            if (keys.length == 0) {
                System.out.println("no keys in this file, will skip");
                continue;
                //throw new RuntimeException("no keys in this file");
            }
            //String[] keys = (String[])keysO;
            String firstKey = (String) keys[0];
            CompilationUnit dom = ast.astRootMap.get(firstKey);
            for (String key : ast.astRootMap.keySet()) {
                CompilationUnit root = ast.astRootMap.get(key);
                root.accept(new MyVisitor());
                //System.out.println("Applying visitor to " + key);
                //root.accept(visitor);
            }

            ImportDeclaration impDecl = dom.getAST().newImportDeclaration();
            Name name = dom.getAST().newName("merkle");
            impDecl.setName(name);
            impDecl.setOnDemand(true);

            ImportDeclaration impDecl2 = dom.getAST().newImportDeclaration();
            Name name2 = dom.getAST().newName("merkle.wrapper");
            impDecl2.setName(name2);
            impDecl2.setOnDemand(true);

            //Block b = Utils.createASTForStatements(code);
            //ImportDeclaration imp = (ImportDeclaration)ASTNode.copySubtree(dom.getAST(), (ImportDeclaration)(b.statements().get(0)));

            List imports = dom.imports();
            imports.add(impDecl);
            imports.add(impDecl2);

            ast.writeToFiles();
        }

        for (String s : MyVisitor.staticClasses) {
            System.out.println("MerkleTreeInstance.addStatic(Class.forName(\"" + s + "\"));");
        }

        String source = "";
        source += "package JDT;\n";
        source += "\n";
        source += "import merkle.*;\n";
        source += "import merkle.wrapper.*;\n";
        source += "\n";
        source += "public class AddStatic {\n";
        source += "\tpublic AddStatic() {\n";
        source += "\t\ttry {\n";
        for (String s : MyVisitor.staticClasses) {
            source += "\t\t\t" + "MerkleTreeInstance.addStatic(Class.forName(\"" + s + "\"));\n";
        }
        source += "\t\t} catch (ClassNotFoundException e) {\n";
        source += "\t\t\te.printStackTrace();\n";
        source += "\t\t}\n";
        source += "\t}\n";
        source += "}\n";


        //System.out.println("Source code:\n"+source);
        //System.out.println();
        try {
            File file = new File(outputDir, "JDT/AddStatic.java");
            file.getParentFile().mkdirs();
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(source.getBytes());
            fos.flush();
            fos.close();
        } catch (Exception e) {//Catch exception if any
            e.printStackTrace();
        }

        if (dontwork.isEmpty()) {
            System.out.println("Instrumentation finished successfully");
        } else {
            System.out.println("Instrumentation was unsuccessful");
            System.out.println(dontwork.size() + " files not properly instrumented");
            for (String s : dontwork) {
                System.out.println(s);
            }
        }

    }

}

