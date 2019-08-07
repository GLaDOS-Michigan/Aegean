package mantis.ftinst;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the compute method that handles the global variables.
 * Adds the print method that handles the global variables.
 * Adds the print and compute calls in the run method of MantisShutdownThread.
 **/
public class GlobalProcessMantisVisitor extends ASTVisitor {

    public void endVisit(TypeDeclaration classDecl) {
        String className = classDecl.getName().getFullyQualifiedName();

        if (className.equals("Mantis")) {

            // Add compute and print methods
            List body = classDecl.bodyDeclarations();
            // collect counters to compute, print, and do dummyread
            List<String> counterList = new ArrayList<String>(BranchVisitor.globalList);
            counterList.addAll(LoopVisitor.globalList);
            counterList.addAll(BlockVisitor.globalList);
            counterList.addAll(MethodVisitor.globalList);
            counterList.addAll(ExceptionVisitor.globalList);

            int numCounters = counterList.size();
            for (int i = 0; i < numCounters; i += 1000) {
                int begin = i;
                int end = i + 1000;
                if (end > numCounters)
                    end = numCounters;
                if (Config.UseThreadLocal) {
                    addCounterComputingMethods(classDecl.getAST(), body, counterList, (int) (i / 1000), begin, end);
                }
                addCounterPrintingMethods(classDecl.getAST(), body, counterList, (int) (i / 1000), begin, end);
                addCounterDummyReadMethods(classDecl.getAST(), body, counterList, (int) (i / 1000), begin, end);
            }

            List<String> varList = new ArrayList<String>(VarValueVisitor.mapVarToType.keySet());
            int numVars = varList.size();
            for (int i = 0; i < numVars; i += 1000) {
                int begin = i;
                int end = i + 1000;
                if (end > numVars)
                    end = numVars;
                if (Config.UseThreadLocal) {
                    addVarComputingMethods(classDecl.getAST(), body, varList, (int) (i / 1000), begin, end);
                }
                addVarPrintingMethods(classDecl.getAST(), body, varList, (int) (i / 1000), begin, end);
                addVarDummyReadMethods(classDecl.getAST(), body, varList, (int) (i / 1000), begin, end);
            }

            String code = "";
            if (Config.UseThreadLocal) {
                code = "public static void compute(){\n";
                for (int i = 0; numCounters > 0 && i <= numCounters / 1000; i++) {
                    code += "computeCounters" + i + "();\n";
                }
                for (int i = 0; numVars > 0 && i <= numVars / 1000; i++) {
                    code += "computeVars" + i + "();\n";
                }
                code += "}\n";
            }

            code += "public static void print(){\n";
            for (int i = 0; numCounters > 0 && i <= numCounters / 1000; i++) {
                code += "printCounters" + i + "();\n";
            }
            for (int i = 0; numVars > 0 && i <= numVars / 1000; i++) {
                code += "printVars" + i + "();\n";
            }
            code += "}\n";

            code += "public static void dummyRead(){\n";
            for (int i = 0; numCounters > 0 && i <= numCounters / 1000; i++) {
                code += "dummyReadCounters" + i + "();\n";
            }
            for (int i = 0; numVars > 0 && i <= numVars / 1000; i++) {
                code += "dummyReadVars" + i + "();\n";
            }
            code += "}\n";

            List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
            Utils.prependASTNodes(classDecl.getAST(), body, newList);

        } else if (className.equals("MantisShutdownThread")) {
            AST ast = classDecl.getAST();
            // make it Thread
            classDecl.setSuperclassType(ast.newSimpleType(ast.newName("Thread")));
            String code = "public void run() {\n"
                    + "java.lang.System.out.println(\"Shutdown Thread: running\");\n";
            code += "Mantis.log();\n";
            if (Config.UseThreadLocal) {
                code += "Mantis.compute();\n";
            }
            code += "Mantis.print();\n" + "}\n";
            List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
            Utils.prependASTNodes(ast, classDecl.bodyDeclarations(), newList);
        }

    }


    private void addCounterDummyReadMethods(AST ast, List classBody, List<String> counterList, int idx, int begin, int end) {
        String code = "private static void dummyReadCounters" + idx + "(){\nint c;\n";
        for (int i = begin; i < end; i++) {
            String counterName = counterList.get(i);
            code += "c = " + counterName + ";\n";
        }
        code += "\n}\n";
        List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
        Utils.appendASTNodes(ast, classBody, newList);
    }

    private void addVarDummyReadMethods(AST ast, List classBody, List<String> varList, int idx, int begin, int end) {
        String code = "private static void dummyReadVars" + idx + "(){\nint v;\n";
        code += "int i;";
//	    for(int i=begin; i < end; i++){
//	        String varName = varList.get(i);
//	        code += "java.lang.System.out.println(\""+varName+": \" + " + varName + ");\n";
//	        code += "java.lang.System.out.print(\""+varName+"_data:\");\n";
//	        code += "for (i = 0; i < " + varName+"_data.length; ++i){\n"
//	        + "java.lang.System.out.print(\" \" + " + varName+"_data[i]);\n}\n";
//	        code += "java.lang.System.out.println();";
//	    }
        code += "//TODO : ADD DUMMY READ FOR VAR\n";
        code += "\n}\n";
        List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
        Utils.appendASTNodes(ast, classBody, newList);
    }

    private void addCounterComputingMethods(AST ast, List classBody, List<String> counterList, int idx, int begin, int end) {
        String code = "private static void computeCounters" + idx + "(){\n";
        code += "java.util.Set es = Mantis.globalInst.entrySet();\n"
                + "java.util.Iterator it = es.iterator();\n"
                + "while(it.hasNext()) {\n"
                + "java.util.Map.Entry pairs = (java.util.Map.Entry)it.next();\n"
                + " MantisInstObj instobj = (MantisInstObj)pairs.getValue();\n";
        for (int i = begin; i < end; i++) {
            code += counterList.get(i) + " += instobj." + counterList.get(i) + ";\n";
        }
        code += "\n}\n}\n";
        List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
        Utils.appendASTNodes(ast, classBody, newList);
    }

    private void addCounterPrintingMethods(AST ast, List classBody, List<String> counterList, int idx, int begin, int end) {

        String code = "private static void printCounters" + idx + "(){\n";
        for (int i = begin; i < end; i++) {
            String counterName = counterList.get(i);
            code += "java.lang.System.out.println(\"" + counterName + ": \" + " + counterName + ");\n";
        }
        code += "\n}\n";
        List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
        Utils.appendASTNodes(ast, classBody, newList);
    }


    private void addVarComputingMethods(AST ast, List classBody, List<String> varNameList, int idx, int begin, int end) {
        String code = "private static void computeVars" + idx + "(){\n";

        code += "java.util.Set es2 = Mantis.globalInstVar.entrySet();\n"
                + "int[] cnt = new int[5];\n"
                + "java.util.Iterator it2;\n"
                + "int i;\n";

        for (int i = begin; i < end; i++) {
            String varName = varNameList.get(i);
            code += "for(i = 0; i < cnt.length; ++i) { cnt[i] = 0; }\n"
                    + "it2 = es2.iterator();\n"
                    + "while(it2.hasNext()){\n"
                    + "java.util.Map.Entry pairs = (java.util.Map.Entry)it2.next();\n"
                    + "MantisInstVarObj instobj = (MantisInstVarObj)pairs.getValue();\n"
                    + "if( " + varName + " < instobj." + varName + " ) " + varName + " = instobj." + varName + ";\n"
                    + "for(i = 0; i < instobj." + varName + "; ++i){\n";


            String type = VarValueVisitor.mapVarToType.get(varName);

            // aggregate char, byte, boolean as int
            if ((type.equals("char") || type.equals("byte") || type.equals("boolean"))) {
                type = "int";
            }

            // convert boolean to integer
            if (VarValueVisitor.mapVarToType.get(varName).equals("boolean")) {
                code += type + " tmp = " + varName + "_data[i];";
                code += "tmp += (instobj." + varName + "_data[i]?1:0);\n";
                code += varName + "_data[i] = tmp;\n";
                //code += varName+"_data[i] += (instobj."+varName+"_data[i]?1:0);\n";
            } else {
                code += type + " tmp = " + varName + "_data[i];";
                code += "tmp += instobj." + varName + "_data[i];\n";
                code += varName + "_data[i] = tmp;\n";
                //code += varName+"_data[i] += instobj."+varName+"_data[i];\n";
            }

            code += "cnt[i]++;\n}\n}"
                    + "for(i=0; i < " + varName + "_data.length; ++i){\n"
                    + "if(cnt[i] > 0){\n" + type + " tmp = " + varName + "_data[i];\n"
                    + "tmp /= cnt[i];\n"
                    + varName + "_data[i] = tmp;\n"
                    + "} else " + varName + "_data[i] = -999999;\n}\n";
            ;
        }
        code += "\n}\n";

        List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
        Utils.appendASTNodes(ast, classBody, newList);
    }

    private void addVarPrintingMethods(AST ast, List classBody, List<String> varList, int idx, int begin, int end) {
        String code = "private static void printVars" + idx + "(){\n";
        code += "int i;";
        for (int i = begin; i < end; i++) {
            String varName = varList.get(i);
            code += "java.lang.System.out.println(\"" + varName + ": \" + " + varName + ");\n";
            code += "java.lang.System.out.print(\"" + varName + "_data:\");\n";
            code += "for (i = 0; i < " + varName + "_data.length; ++i){\n"
                    + "java.lang.System.out.print(\" \" + " + varName + "_data[i]);\n}\n";
            code += "java.lang.System.out.println();";
        }
        code += "\n}\n";
        List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
        Utils.appendASTNodes(ast, classBody, newList);
    }

}
