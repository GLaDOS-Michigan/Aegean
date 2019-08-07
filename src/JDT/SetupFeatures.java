package mantis.ftinst;

import org.eclipse.jdt.core.dom.*;

import java.util.List;

/**
 * Adds import statements to source files
 * Adds MantisInstObj member variables - branch and loop counters
 * Adds MantisInstVarObj member variables - variable values
 * Adds Mantis static member variables
 * Adds some initial statements and final statements in the main method of the main thread and run methods of other threads
 */
public class SetupFeatures extends ASTVisitor {

    /**
     * Add fields to Mantis, MantisInstObj and MantisVarObj class
     * Add some initial statements to Mantis classes
     */
    @SuppressWarnings("unchecked")
    public void endVisit(TypeDeclaration classDecl) {
        String className = classDecl.getName().getIdentifier();
        AST ast = classDecl.getAST();

        if (className.equals("Mantis")) {
            String code = "";
            if (Config.UseThreadLocal) {
                code += "public static java.lang.ThreadLocal inst = new java.lang.ThreadLocal();\n"
                        + "public static java.lang.ThreadLocal instVar = new java.lang.ThreadLocal();\n"
                        + "public static java.util.Map globalInst = new java.util.HashMap();\n"
                        + "public static java.util.Map globalInstVar = new java.util.HashMap();\n";
            }
            code += "public static long startTime;\n"
                    + "public static long endTime;\n"
                    + "public static void log() {\n"
                    + "java.lang.System.out.println(\"mantist_Time: \" + ((double)(endTime - startTime))/1000000000.0);\n";
            if (Config.UseThreadLocal) {
                code += "java.util.Iterator it = globalInst.entrySet().iterator();\n"
                        + "while (it.hasNext()) {\n"
                        + "java.util.Map.Entry pairs = (java.util.Map.Entry) it.next();\n"
                        + "java.lang.System.out.println(\"Thread Log Start CN: \" + pairs.getKey());\n"
                        + "((MantisInstObj) pairs.getValue()).print();\n"
                        + "java.lang.System.out.println(\"Thread Log End CN: \" + pairs.getKey());\n"
                        + "}\n"
                        + "it = globalInstVar.entrySet().iterator();\n"
                        + "while (it.hasNext()) { java.util.Map.Entry pairs = (java.util.Map.Entry) it.next();\n"
                        + "java.lang.System.out.println(\"Thread Log Start VV: \" +\n"
                        + "pairs.getKey());\n"
                        + "((MantisInstVarObj) pairs.getValue()).print();\n"
                        + "java.lang.System.out.println(\"Thread Log End VV: \" +\n"
                        + "pairs.getKey());\n"
                        + "}\n";
            }
            code += "}\n"
                    + "public static void startTime() { startTime = java.lang.System.nanoTime(); }\n"
                    + "public static void endTime() { endTime = java.lang.System.nanoTime(); }\n";

            List newList = Utils.createASTForClassBodyDecl(code).bodyDeclarations();
            Utils.prependASTNodes(classDecl.getAST(), classDecl.bodyDeclarations(), newList);

            // Add fields for loop counters
            for (String counter : LoopVisitor.globalList) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, counter, true));
            }
            // Add fields for branch counters
            for (String counter : BranchVisitor.globalList) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, counter, true));
            }
            // Add fields for block counters
            for (String counter : BlockVisitor.globalList) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, counter, true));
            }
            // Add fields for method counters
            for (String counter : MethodVisitor.globalList) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, counter, true));
            }
            // Add fields for exception counters
            for (String counter : ExceptionVisitor.globalList) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, counter, true));
            }

            // Add fields for variable value tracking
            for (String varName : VarValueVisitor.mapVarToType.keySet()) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, varName, true));
                classDecl.bodyDeclarations().add(createVarTrackingField(ast, varName, true));
            }

        } else if (className.equals("MantisInstObj")) {
            // Add fields for loop counters
            for (String counter : LoopVisitor.globalList) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, counter, false));
            }
            // Add fields for branch counters
            for (String counter : BranchVisitor.globalList) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, counter, false));
            }
            // Add fields for block counters
            for (String counter : BlockVisitor.globalList) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, counter, false));
            }
            // Add fields for method counters
            for (String counter : MethodVisitor.globalList) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, counter, false));
            }
            // Add fields for exception counters
            for (String counter : ExceptionVisitor.globalList) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, counter, true));
            }

        } else if (className.equals("MantisInstVarObj")) {
            // Add fields for variable value tracking
            for (String varName : VarValueVisitor.mapVarToType.keySet()) {
                classDecl.bodyDeclarations().add(createFieldDeclForCounter(ast, varName, false));
                classDecl.bodyDeclarations().add(createVarTrackingField(ast, varName, false));
            }
        }

    }

    /**
     * Insert import declarations of Mantis classes
     */
    @SuppressWarnings("unchecked")
    public void endVisit(CompilationUnit cu) {
        // not adding the import declarations to mantis classes themselves
        if (cu.getPackage() != null &&
                cu.getPackage().getName().getFullyQualifiedName().equals(
                        FeatureInstrumentor.MANTIS_PACKAGE_NAME)) {
            return;
        }

        AST ast = cu.getAST();
        cu.imports().add(createMantisClassesImportDecl(ast, "Mantis"));
        if (Config.UseThreadLocal) {
            cu.imports().add(createMantisClassesImportDecl(ast, "MantisInstObj"));
            cu.imports().add(createMantisClassesImportDecl(ast, "MantisInstVarObj"));
        }
        cu.imports().add(createMantisClassesImportDecl(ast, "MantisShutdownThread"));
    }

    /**
     * Create an an ASTNode for an import declaration for a mantis class
     *
     * @param ast
     * @param classname
     * @return
     */
    private ImportDeclaration createMantisClassesImportDecl(AST ast, String classname) {
        classname = FeatureInstrumentor.MANTIS_PACKAGE_NAME + "." + classname;
        Name name = ast.newName(classname);
        ImportDeclaration importDecl = ast.newImportDeclaration();
        importDecl.setName(name);
        return importDecl;
    }

    /**
     * Create an ASTNode for field declaration for a counter
     *
     * @param ast
     * @param counterName
     * @param asStatic    whether this field is static or not
     * @return Created ASTNode of type FieldDeclaration
     */
    @SuppressWarnings("unchecked")
    private FieldDeclaration createFieldDeclForCounter(AST ast, String counterName, boolean asStatic) {
        VariableDeclarationFragment vdf = ast.newVariableDeclarationFragment();
        vdf.setName(ast.newSimpleName(counterName));
        FieldDeclaration fd = ast.newFieldDeclaration(vdf);
        fd.setType(ast.newPrimitiveType(PrimitiveType.INT));
        fd.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        if (asStatic) {
            fd.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
        }
        return fd;
    }

    /**
     * Create AST for variable tracking code
     * (e.g. public int[] myVarName_data = new int[5]; )
     *
     * @param ast
     * @param varName
     * @param asStatic whether this field is static or not
     * @return
     */
    @SuppressWarnings("unchecked")
    private FieldDeclaration createVarTrackingField(AST ast, String varName, boolean asStatic) {
        String type = VarValueVisitor.mapVarToType.get(varName);

        // aggregate char, byte, boolean as int
        if (asStatic &&
                (type.equals("char") || type.equals("byte") || type.equals("boolean"))) {
            type = "int";
        }

        VariableDeclarationFragment vdf = ast.newVariableDeclarationFragment();
        vdf.setName(ast.newSimpleName(varName + "_data"));
        ArrayCreation ac = ast.newArrayCreation();
        ac.setType(ast.newArrayType(ast.newPrimitiveType(PrimitiveType.toCode(type))));
        ac.dimensions().add(ast.newNumberLiteral("" + VarValueVisitor.CUTOFF));
        vdf.setInitializer(ac);
        FieldDeclaration fd = ast.newFieldDeclaration(vdf);
        fd.setType(ast.newArrayType(ast.newPrimitiveType(PrimitiveType.toCode(type))));
        fd.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        if (asStatic) {
            fd.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
        }
        return fd;
    }

    /**
     * Add initial statements to start/stop methods and run() methods of Thread
     */
    public void endVisit(MethodDeclaration methodDecl) {
        String methodName = methodDecl.getName().getIdentifier();
        Block body = methodDecl.getBody();

        if (methodDecl.resolveBinding() == null) {
            System.out.println("No method binding : " + methodDecl.getName());
            System.exit(-1);
        }

        //String packageName = methodDecl.resolveBinding().getDeclaringClass().getPackage().getName();
        String declaringClassName = methodDecl.resolveBinding().getDeclaringClass().getQualifiedName();
        String fullname = declaringClassName + "." + methodName;

        if (Config.StartMethodSet.contains(fullname)) {
            if (!methodName.equals("main") && !methodName.equals("run")) {
                body = prependInitStatements(body);
            }
            body = prependShutdownStatements(body);
            // add code to capture start time
            body = prependCodeStartTime(body);
        }

        if (Config.StopMethodSet.contains(fullname)) {
            if (!methodName.equals("main") && !methodName.equals("run")) {
                body = appendCopyStatements(body);
            }
            // add code to capture end time
            body = appendCodeEndTime(body);
        }

        if (methodName.equals("main") &&
                declaringClassName.equals(Config.MainClassName)) {

            // add initialization at the beginning
            body = prependInitStatements(body);
            // add finialization at the end
            body = appendCopyStatements(body);
            appendDummyReadCall(body);
        }

        // instrument run() method of Thread
        if (methodName.equals("run") && isDeclaredInSubtypeOfThread(methodDecl)
                && !declaringClassName.equals(FeatureInstrumentor.MANTIS_PACKAGE_NAME + ".MantisShutdownThread")) {
            // add initialization at the beginning
            body = prependInitStatements(body);
            // add finialization at the end
            body = appendCopyStatements(body);
        }

    }

    private boolean isDeclaredInSubtypeOfThread(MethodDeclaration methodDecl) {

        ITypeBinding classBinding = methodDecl.resolveBinding().getDeclaringClass();
        while (classBinding != null) {
            String fullname = classBinding.getQualifiedName();
            if (fullname.equals("java.lang.Thread")) {
                return true;
            }
            classBinding = classBinding.getSuperclass();
        }
        return false;
    }

    private Block prependInitStatements(Block b) {
        if (!Config.UseThreadLocal) return b;
        String codeToAdd = "Mantis.inst.set(new MantisInstObj());";
        codeToAdd += "Mantis.instVar.set(new MantisInstVarObj());";
        Block stmtsToAdd = Utils.createASTForStatements(codeToAdd);
        return Utils.prependASTNodes(b, stmtsToAdd.statements());
    }

    private Block prependShutdownStatements(Block b) {
        String codeToAdd = "java.lang.Runtime.getRuntime().addShutdownHook(new MantisShutdownThread());";
        Block stmtsToAdd = Utils.createASTForStatements(codeToAdd);
        return Utils.prependASTNodes(b, stmtsToAdd.statements());
    }

    private Block prependCodeStartTime(Block b) {
        String codeToAdd = "Mantis.startTime();";
        Block stmtsToAdd = Utils.createASTForStatements(codeToAdd);
        return Utils.prependASTNodes(b, stmtsToAdd.statements());
    }

    private Block appendCopyStatements(Block b) {
        if (!Config.UseThreadLocal) return b;
        String codeToAdd = "Mantis.globalInst.put(new java.lang.Long(java.lang.Thread.currentThread().getId()),\n"
                + "new MantisInstObj((MantisInstObj)Mantis.inst.get()));\n"
                + "Mantis.globalInstVar.put(new java.lang.Long(java.lang.Thread.currentThread().getId()),\n"
                + "new MantisInstVarObj((MantisInstVarObj)Mantis.instVar.get()));";
        Block stmtsToAdd = Utils.createASTForStatements(codeToAdd);
        return Utils.appendASTNodes(b, stmtsToAdd.statements());
    }

    private Block appendCodeEndTime(Block b) {
        String codeToAdd = "Mantis.endTime();";
        Block stmtsToAdd = Utils.createASTForStatements(codeToAdd);
        return Utils.appendASTNodes(b, stmtsToAdd.statements());
    }

    private Block appendDummyReadCall(Block b) {
        String codeToAdd = "Mantis.dummyRead();";
        Block stmtsToAdd = Utils.createASTForStatements(codeToAdd);
        return Utils.appendASTNodes(b, stmtsToAdd.statements());
    }


}
