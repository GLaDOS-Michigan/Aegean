package mantis.ftinst;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Parse input files to create ASTes
 * Apply ASTVisitors to the ASTes
 * Output to files
 */
public class FeatureInstrumentor {
    // Package name of Mantis classes
    protected static String MANTIS_PACKAGE_NAME = "mantis";
    // map from Mantis class name to initial code string
    private static HashMap<String, String> mantisClassInitialCodeMap = new HashMap<String, String>();
    // Map from class name to AST root node 
    private HashMap<String, CompilationUnit> astRootMap;

    public FeatureInstrumentor() {
        astRootMap = new HashMap<String, CompilationUnit>();

        // Add input source files
        LinkedList<String> l = new LinkedList<String>();
        l.addAll(Config.SrcFileList);

        // Create ASTes
        parse(l.toArray(new String[0]), Config.SrcHomeDir, Config.ClassPath);
        // Create ASTes for Mantis classes
        createMantisClassASTes();

    }

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
                        String k = FeatureInstrumentor.fullSrcPathToClassName(sourceFilePath);
                        ast.recordModifications();
                        astRootMap.put(k, ast);
                    }
                }, null);

        if (astRootMap.size() != srcFiles.length) {
            System.err.println("Not all files are parsed successfuly.");
            System.exit(-1);
        }

    }

    /**
     * Apply an ASTVisitor instance to the ASTes
     *
     * @param visitor an ASTVisitor to apply
     */
    public void applyVisitor(ASTVisitor visitor) {
        for (String key : astRootMap.keySet()) {
            CompilationUnit root = astRootMap.get(key);
            //System.out.println("Applying visitor to " + key);
            root.accept(visitor);
        }
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
        if (className.startsWith(MANTIS_PACKAGE_NAME)) {
            doc = new Document(mantisClassInitialCodeMap.get(className));
        } else {
            String src;
            src = FeatureInstrumentor.classNameToFullSrcPath(className);
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
        }

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
        File outdir = new File(Config.OutputDir);
        if (!outdir.exists()) {
            outdir.mkdirs();
        }
        File outfile = new File(FeatureInstrumentor.classNameToFullDstPath(className));
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

    /**
     * Create ASTes for Mantis classes
     */
    private void createMantisClassASTes() {
        createMantisClassAST("Mantis");
        if (Config.UseThreadLocal) {
            createMantisClassAST("MantisInstObj");
            createMantisClassAST("MantisInstVarObj");
        }
        createMantisClassAST("MantisShutdownThread");
    }


    /**
     * Create an AST of a bare bones Mantis class
     *
     * @param mantisClassName
     */
    private void createMantisClassAST(String mantisClassName) {
        String code = "";
        code += "package " + MANTIS_PACKAGE_NAME + ";\n";
        code += "\n\npublic class " + mantisClassName + " {\n"
                + "public " + mantisClassName + "(){ super();}\n"
                + "}\n";
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        String fullName = MANTIS_PACKAGE_NAME + "." + mantisClassName;
        mantisClassInitialCodeMap.put(fullName, code);
        parser.setSource(code.toCharArray());
        parser.setUnitName("mantis/" + mantisClassName + ".java");
        parser.setEnvironment(null, null, null, true);
        parser.setBindingsRecovery(true);
        CompilationUnit astRootNode = (CompilationUnit) parser.createAST(null);
        astRootNode.recordModifications();

        astRootMap.put(fullName, astRootNode);
    }

    private static String classNameToFullSrcPath(String className) {

        for (String srcDir : Config.SrcHomeDir) {
            String filename =
                    srcDir + File.separator + className.replace('.', File.separatorChar) + ".java";
            File f = new File(filename);
            if (f.exists()) return filename;
        }

        return null;
//        return Config.SrcHomeDir + File.separator + className.replace('.', File.separatorChar ) + ".java";
    }

    private static String classNameToFullDstPath(String className) {
        return Config.OutputDir + File.separator + className.replace('.', File.separatorChar) + ".java";
    }

    private static String fullSrcPathToClassName(String fullPath) {
        String srchomedir = null;
        for (String srcHomeDir : Config.SrcHomeDir) {
            if (fullPath.startsWith(srcHomeDir)) {
                srchomedir = srcHomeDir;
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
     * @param argv
     * @throws IOException
     * @throws CoreException
     */
    public static void main(String[] argv) throws IOException, CoreException {

        Config.readConfig(argv);

        FeatureInstrumentor featInst = new FeatureInstrumentor();
        
        /*featInst.applyVisitor(new VarValueVisitor());
        featInst.applyVisitor(new BranchVisitor());
        featInst.applyVisitor(new LoopVisitor());
//        featInst.applyVisitor(new BlockVisitor());
        featInst.applyVisitor(new MethodVisitor());
        featInst.applyVisitor(new ExceptionVisitor());
        
        featInst.applyVisitor(new SetupFeatures());
        featInst.applyVisitor(new LocalProcessMantisVisitor());
        featInst.applyVisitor(new GlobalProcessMantisVisitor());*/

        featInst.writeToFiles();
        System.out.println("Instrumentation finished!");
    }

}
