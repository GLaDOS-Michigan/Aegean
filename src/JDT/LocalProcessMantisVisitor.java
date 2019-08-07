package mantis.ftinst;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds copy and print method declarations in MantisInstObj and MantisInstVarObj.
 * Adds the copy calls in the constructor and the print calls in the print method.
 **/
public class LocalProcessMantisVisitor extends ASTVisitor {

    public void endVisit(TypeDeclaration classDecl) {
        String className = classDecl.getName().getFullyQualifiedName();

        if (className.equals("MantisInstObj")) {
            List body = classDecl.bodyDeclarations();
            // collect counters to print and copy
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
                addCounterCopyingMethods(classDecl.getAST(), body, counterList, (int) (i / 1000), begin, end);
                addCounterPrintingMethods(classDecl.getAST(), body, counterList, (int) (i / 1000), begin, end);

            }
            String code = "public void print(){\n";
            for (int i = 0; numCounters > 0 && i <= numCounters / 1000; i++) {
                code += "print" + i + "();\n";
            }
            code += "}\n";

            List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
            Utils.prependASTNodes(classDecl.getAST(), body, newList);

            // add constructor that calls copy() method
            code = "public MantisInstObj(MantisInstObj obj){\n";
            for (int i = 0; numCounters > 0 && i <= numCounters / 1000; i++) {
                code += "this.copy" + i + "(obj);\n";
            }
            code += "}\n";
            newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
            Utils.prependASTNodes(classDecl.getAST(), body, newList);


        } else if (className.equals("MantisInstVarObj")) {
            List body = classDecl.bodyDeclarations();
            List<String> varList = new ArrayList<String>(VarValueVisitor.mapVarToType.keySet());
            int numVars = varList.size();
            for (int i = 0; i < numVars; i += 1000) {
                int begin = i;
                int end = i + 1000;
                if (end > numVars)
                    end = numVars;
                addVarCopyingMethods(classDecl.getAST(), body, varList, (int) (i / 1000), begin, end);
                addVarPrintingMethods(classDecl.getAST(), body, varList, (int) (i / 1000), begin, end);

            }

            String code = "public void print(){\n";
            for (int i = 0; numVars > 0 && i <= numVars / 1000; i++) {
                code += "print" + i + "();\n";
            }
            code += "}\n";

            List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
            Utils.prependASTNodes(classDecl.getAST(), body, newList);

            // add constructor that calls copy() method
            code = "public MantisInstVarObj(MantisInstVarObj obj){\n";
            for (int i = 0; numVars > 0 && i <= numVars / 1000; i++) {
                code += "this.copy" + i + "(obj);\n";
            }
            code += "}\n";
            newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
            Utils.prependASTNodes(classDecl.getAST(), body, newList);
        }

    }

    private void addCounterPrintingMethods(AST ast, List classBody, List<String> counterList, int idx, int begin, int end) {
        String code = "private void print" + idx + "(){\n";
        for (int i = begin; i < end; i++) {
            String counterName = counterList.get(i);
            code += "java.lang.System.out.println(\"" + counterName + ": \" + " + counterName + ");\n";
        }
        code += "\n}\n";
        List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
        Utils.appendASTNodes(ast, classBody, newList);
    }

    private void addCounterCopyingMethods(AST ast, List classBody, List<String> counterList, int idx, int begin, int end) {
        String code = "private void copy" + idx + "(MantisInstObj obj){\n";
        for (int i = begin; i < end; i++) {
            String counterName = counterList.get(i);
            code += counterName + " = obj." + counterName + ";\n";
        }
        code += "\n}\n";
        List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
        Utils.appendASTNodes(ast, classBody, newList);
    }

    private void addVarPrintingMethods(AST ast, List classBody, List<String> varList, int idx, int begin, int end) {
        String code = "private void print" + idx + "(){\n";
        code += "int i;";
        for (int i = begin; i < end; i++) {
            String varName = varList.get(i);
            code += "java.lang.System.out.println(\"" + varName + ": \" + " + varName + ");\n";
            code += "java.lang.System.out.println(\"" + varName + "_data:\");\n";
            code += "for (i = 0; i < " + varName + "_data.length; ++i){\n"
                    + "java.lang.System.out.print(\" \" + " + varName + "_data[i]);\n}\n";
            code += "java.lang.System.out.println();";
        }
        code += "\n}\n";
        List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
        Utils.appendASTNodes(ast, classBody, newList);
    }

    private void addVarCopyingMethods(AST ast, List classBody, List<String> counterList, int idx, int begin, int end) {
        String code = "private void copy" + idx + "(MantisInstVarObj obj){\n";
        for (int i = begin; i < end; i++) {
            String counterName = counterList.get(i);
            code += counterName + " = obj." + counterName + ";\n";
            code += "java.lang.System.arraycopy(obj." + counterName + "_data, 0, "
                    + counterName + "_data, 0, obj." + counterName + "_data.length);\n";
        }
        code += "\n}\n";
        List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
        Utils.appendASTNodes(ast, classBody, newList);
    }

}
