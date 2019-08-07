/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package JDT;

import mantis.ftinst.Utils;
import org.eclipse.jdt.core.dom.*;

import java.util.HashSet;
import java.util.List;
import java.util.Stack;

/**
 * Visits the appropriate <ASTNode>s and performs the corresponding actions.
 * The <visit> methods are executed when the node is first visited.
 * The <endVisit> methods are executed after all of the node's children
 * have been visited.
 *
 * @author manos
 */
public class MyVisitor extends ASTVisitor {
    protected Stack<String> methodStack = new Stack<String>();
    public static HashSet<String> staticClasses = new HashSet<String>();

    @Override
    public boolean visit(TypeDeclaration typeDec) {
        //System.out.println("Visiting type declaration\n"+typeDec.toString());
        return true;
    }

    /*@Override
    public void endVisit(TypeDeclaration typeDec) {
        System.out.println("End Visiting type declaration\n"+typeDec.toString());
    }*/

    @Override
    public boolean visit(FieldDeclaration fieldDec) {
        //System.out.println("Visiting field declaration: "+fieldDec.toString());
        List<VariableDeclarationFragment> list = fieldDec.fragments();
        for (VariableDeclarationFragment vdf : list) {
            Name name = vdf.getName();
            //System.out.println("Name: "+name);
            if (name.resolveBinding() != null && name.resolveBinding().getKind() == IBinding.VARIABLE) {
                IVariableBinding iVar = (IVariableBinding) name.resolveBinding();
                if (iVar.isField()) {
                    if (Modifier.isStatic(iVar.getModifiers()) && !Modifier.isFinal(iVar.getModifiers())) {
                        String declaringClass = iVar.getDeclaringClass().getQualifiedName();
                        //System.out.println("It's a non-final, static field. Declaring class: "+declaringClass);
                        staticClasses.add(declaringClass);
                    } else {
                        //System.out.println("This field is either non-static or final. In either case, we don't care");
                    }
                } else {
                    throw new RuntimeException("This is not a field: " + name.getFullyQualifiedName() + ". What on earth did you declare");
                }
            }
        }
        return true;
    }

    @Override
    public boolean visit(FieldAccess fieldAcc) {
        /*System.out.println("Visiting field access: "+fieldAcc.toString());
        System.out.println("Expression: "+fieldAcc.getExpression());
        System.out.println();*/
        return true;
    }

    @Override
    public boolean visit(Assignment assign) {
        Statement parentStatement = getParentStatement(assign);
        if (parentStatement == null) {   // this means that this is not part of a statement
            return true;                // so I will not add instrumentation
        }
        ////System.out.println("Visiting assignment: "+assign.toString()+" parentStatement: "+parentStatement.toString());
        //ASTNode parentsParent = parentStatement.getParent();
        Expression lefthand = assign.getLeftHandSide();
        //System.out.println("Left-hand side: "+lefthand);

        handleExpression(lefthand, parentStatement);
        ////System.out.println();
        //System.out.println(assign.getLeftHandSide().getClass()+" "+assign.getOperator()+" "+assign.getRightHandSide().getClass());
        return true;
    }

    @Override
    public boolean visit(PrefixExpression prefix) {
        //System.out.println("Prefix expression "+prefix.getOperand()+ " "+ prefix.getOperator());
        String operator = prefix.getOperator().toString();
        if (!operator.equalsIgnoreCase("--") && !operator.equalsIgnoreCase("++")) {
            return true;
        }
        Statement parentStatement = getParentStatement(prefix);
        if (parentStatement == null) {   // this means that this is not part of a statement
            return true;                // so I will not add instrumentation
        }
        //ASTNode parentsParent = parentStatement.getParent();
        Expression operand = prefix.getOperand();
        handleExpression(operand, parentStatement);
        return true;
    }

    @Override
    public boolean visit(PostfixExpression postfix) {
        //System.out.println("Postfix expression "+postfix.getOperand()+ " "+ postfix.getOperator());
        String operator = postfix.getOperator().toString();
        if (!operator.equalsIgnoreCase("--") && !operator.equalsIgnoreCase("++")) {
            return true;
        }
        Statement parentStatement = getParentStatement(postfix);
        if (parentStatement == null) {   // this means that this is not part of a statement
            return true;                // so I will not add instrumentation
        }
        //ASTNode parentsParent = parentStatement.getParent();
        Expression operand = postfix.getOperand();
        handleExpression(operand, parentStatement);
        return true;
    }

    private void handleExpression(Expression operand, Statement parentStatement) {
        //System.out.println("Handling "+operand+" in statement "+parentStatement);
        MethodDeclaration parentMethod = getParentMethod(parentStatement);
        //System.out.println("Parent method is "+parentMethod.getName());
        boolean isConstructor = false;
        if (parentMethod != null && parentMethod.isConstructor()) {
            isConstructor = true;
        }

        if (parentMethod != null && parentMethod.getName().toString().equalsIgnoreCase("hashCode")) {
            // We do not put updates inside hashCode, as this would create a loop.
            // The assumption is that no consistent state is modified inside a
            // hashCode override. If someone needs to do that, they would have
            // to make that class extend MerkleTreeObject, so the update()
            // would only look at the unique identifier.

            //System.out.println("Expression is inside a hashCode method. Ignoring.");
            return;
        }

        if (operand instanceof Name) {
            Name name = (Name) operand;
            if (name.resolveBinding() != null && name.resolveBinding().getKind() == IBinding.VARIABLE) {
                IVariableBinding iVar = (IVariableBinding) name.resolveBinding();
                // First see if it's a static field
                if (iVar.isField() && Modifier.isStatic(iVar.getModifiers())) {
                    //System.out.println("Static field. Not implemented yet.");
                    //System.out.println("Class: "+iVar.getType().getQualifiedName());
                    //if(!iVar.getType().isPrimitive()) {
                    if (!Modifier.isFinal(iVar.getModifiers()) && !Modifier.isTransient(iVar.getModifiers())) {
                        addUpdateStatement(parentStatement, iVar.getDeclaringClass().getQualifiedName(), true);
                    } else {
                        // Do nothing, it's a final or transient static field
                    }
                    //}
                } else {
                    // Now that we know it's not static, see if it's Simple or Qualified
                    if (name instanceof QualifiedName) {
                        QualifiedName qname = (QualifiedName) name;
                        Name qualifier = qname.getQualifier();
                        //System.out.println("Qualified name "+name+" "+qualifier);
                        SimpleName sn = qname.getName();
                        if (sn.resolveBinding() != null && sn.resolveBinding().getKind() == IBinding.VARIABLE) {
                            IVariableBinding iVarSn = (IVariableBinding) sn.resolveBinding();
                            IAnnotationBinding[] annotations = iVarSn.getDeclaringClass().getAnnotations();
                            for (IAnnotationBinding ann : annotations) {
                                //System.out.println(ann.getName());
                                if (ann.getName().equalsIgnoreCase("TransientObject")) {
                                    //System.out.println("Field Access for a transient object. Skipping.");
                                    return;
                                }
                            }
                        } else {
                            System.out.println("Warning: " + sn + " should be a field");
                        }
                        addUpdateStatement(parentStatement, qualifier.toString());
                    } else if (name instanceof SimpleName) {
                        if (iVar.isField()) {
                            if (!isConstructor) {
                                IAnnotationBinding[] annotations = iVar.getDeclaringClass().getAnnotations();
                                for (IAnnotationBinding ann : annotations) {
                                    //System.out.println(ann.getName());
                                    if (ann.getName().equalsIgnoreCase("TransientObject")) {
                                        //System.out.println("Field Access for a transient object. Skipping.");
                                        return;
                                    }
                                }
                                addUpdateStatement(parentStatement, "this");
                            } else {
                                // System.out.println(parentStatement+" is in a constructor. Skipping.");
                            }
                        } else {
                            //System.out.println("It's a local variable");
                            //Do nothing here, this is a simple name for a local variable
                        }
                    }
                }
            }
        } else if (operand instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess) operand;
            //System.out.println("Field Access "+operand+ " "+fa.getExpression());

            IVariableBinding faBinding = fa.resolveFieldBinding();
            if (faBinding.isField() && Modifier.isStatic(faBinding.getModifiers())) {
                if (!Modifier.isFinal(faBinding.getModifiers()) && !Modifier.isTransient(faBinding.getModifiers())) {
                    addUpdateStatement(parentStatement, faBinding.getDeclaringClass().getQualifiedName(), true);
                } else {
                    // Do nothing, it's a final or transient static field
                }
            } else {
                if (!isConstructor || !fa.getExpression().toString().equalsIgnoreCase("this")) {
                    //if(fa.getExpression().toString().equalsIgnoreCase("this")) {
                    SimpleName sn = fa.getName();
                    if (sn.resolveBinding() != null && sn.resolveBinding().getKind() == IBinding.VARIABLE) {
                        IVariableBinding iVarSn = (IVariableBinding) sn.resolveBinding();
                        IAnnotationBinding[] annotations = iVarSn.getDeclaringClass().getAnnotations();
                        for (IAnnotationBinding ann : annotations) {
                            //System.out.println(ann.getName());
                            if (ann.getName().equalsIgnoreCase("TransientObject")) {
                                //System.out.println("Field Access for a transient object. Skipping.");
                                return;
                            }
                        }
                    } else {
                        System.out.println("Warning: " + sn + " should be a field");
                    }
                    //}

                    addUpdateStatement(parentStatement, fa.getExpression().toString());
                } else {
                    // System.out.println(parentStatement+" is in a constructor and accesses this. Skipping.");
                }
            }


            //System.out.println("FieldAccess: update("+fa.getExpression()+");");
        } else if (operand instanceof ArrayAccess) {
            //System.out.println("ArrayAccess. Not yet implemented");
            ArrayAccess aa = (ArrayAccess) operand;
            addUpdateStatement(parentStatement, aa.getArray().toString());
            //System.out.println("array access: "+aa.getArray()+" "+aa.getIndex());
        } else if (operand instanceof ThisExpression) {
            //System.out.println("This Expression: "+operand);
            addUpdateStatement(parentStatement, "this");
            //System.out.println("ThisExpression: update(this);");
        } else {
            System.out.println("*************\nSomething else\n*************");
        }

    }

    @Override
    public boolean visit(MethodDeclaration methodDec) {
        //System.out.println("Visiting method declaration: "+methodDec.toString());
        //System.out.println("Pushed "+methodDec.getName().getFullyQualifiedName());
        //methodStack.push(methodDec.getName().getFullyQualifiedName());
        //if(methodDec.isConstructor()) {

        //}
        return true;
    }

    @Override
    public boolean visit(MethodInvocation methodInv) {
        ////System.out.println("Visiting method call: "+methodInv.toString());
        Expression expr = methodInv.getExpression();
        Name name = methodInv.getName();
        if (expr == null || name == null) {
            return true;
        }

        if (expr.toString().equalsIgnoreCase("System") && name.toString().equalsIgnoreCase("arraycopy")) {
            String wrap = "SystemWrapper";
            Name newExpr = methodInv.getAST().newName(wrap);
            methodInv.setExpression(newExpr);
        }
        ////System.out.println(name);
        ////System.out.println(expr);
        ////System.out.println();
        //   this.visit
        //System.out.println("Pushed "+methodDec.getName().getFullyQualifiedName());
        //methodStack.push(methodDec.getName().getFullyQualifiedName());
        //if(methodDec.isConstructor()) {

        //}
        return true;
    }

    @Override
    public boolean visit(QualifiedName qualName) {
        //System.out.println("Visiting qualified name: "+qualName.toString());
        return true;
    }

    @Override
    public boolean visit(SimpleName simpleName) {
        /*
        if(simpleName.resolveBinding().getKind() == IBinding.VARIABLE) {
            IVariableBinding iVar = (IVariableBinding)simpleName.resolveBinding();
            if(iVar.isField()) {
                System.out.println("Visiting simple name field: "+simpleName.toString());
                //System.out.println(simpleName.getIdentifier());
                //System.out.println(simpleName.getParent().toString()+" "+simpleName.getParent().getClass());

                //System.out.println(simpleName.getFullyQualifiedName());
                //System.out.println("This is a variable");
            
            
                System.out.println("it's a field");
                //if(ParseAST.fields == null) {
                //    System.out.println("null");
                //}
                if(!methodStack.isEmpty()) {
                    System.out.println("Field in method, adding to fields");
                    ArrayList list = ParseAST.fields.get(methodStack.lastElement());
                    if(list == null) {
                        list = new ArrayList<String>();
                    }
                    if(list.contains(iVar.getName())) {
                        System.out.println("list already contains field "+iVar.getName());
                    } else {
                        System.out.println("Adding field "+iVar.getName()+
                                " to method "+methodStack.lastElement());
                        list.add(iVar.getName());
                        ParseAST.fields.put(methodStack.lastElement(),list);
                    }
                } else {
                    System.out.println("Method stack is empty\n");
                }
            } //else {
              //  System.out.println("it's a local variable");
            //}
        }*/

        return true;
    }

    private ExpressionStatement createUpdateStmt(AST ast, String argName, boolean isStatic) {
        // create a simple counter name
        //globalList.add(counterName);
        // Thread local counter access
        String code = null;
        if (isStatic) {
            code = "MerkleTreeInstance.updateStatic(" + argName + ".class);";
        } else {
            code = "MerkleTreeInstance.update(" + argName + ");";
        }

        Block b = Utils.createASTForStatements(code);
        return (ExpressionStatement) ASTNode.copySubtree(ast, (ExpressionStatement) (b.statements().get(0)));

    }

    private Statement getParentStatement(ASTNode node) {
        while (!(node instanceof Statement)) {
            if (node instanceof TypeDeclaration) {
                return null;
            }
            node = node.getParent();
            ////System.out.println(ASTNode.nodeClassForType(node.getNodeType())+" : "+node.toString());
        }
        ////System.out.println("ASTNode : "+node.toString());
        return (Statement) node;
    }

    private MethodDeclaration getParentMethod(ASTNode node) {
        while (!(node instanceof MethodDeclaration)) {
            if (node instanceof TypeDeclaration) {
                return null;
            }
            node = node.getParent();
            ////System.out.println(ASTNode.nodeClassForType(node.getNodeType())+" : "+node.toString());
        }
        ////System.out.println("ASTNode : "+node.toString());
        return (MethodDeclaration) node;
    }

    public void endVisit(MethodDeclaration methodDecl) {
    /*
        //getting all the field accessed in this method
        ArrayList list = ParseAST.fields.get(methodDecl.getName().getFullyQualifiedName());
        System.out.println("Retrieving list for method "+methodDecl.getName().getFullyQualifiedName());
        if(list == null) {
            System.out.println("Error: null method entry");
            System.exit(-1);
        }
        for(int i=0;i<list.size();i++) {
            String field = (String) list.get(i);
            System.out.println("Field: "+field);
        }

        methodStack.pop();

    	Block body = methodDecl.getBody();

        if(methodDecl.resolveBinding() == null){
            System.out.println("No method binding : " + methodDecl.getName());
            System.exit(-1);
        }

        //String declaringClassName = methodDecl.resolveBinding().getDeclaringClass().getQualifiedName();
        //if(declaringClassName.startsWith(FeatureInstrumentor.MANTIS_PACKAGE_NAME)){
        //    return;
        //}

        Statement newBody = null;
        for(int i=0;i<list.size();i++) {
            String field = (String) list.get(i);
            //System.out.println("Field: "+field);
            newBody = addUpdateStmt(body, methodDecl.isConstructor(), field);
        }
        

        if(newBody != null){
            methodDecl.setBody((Block) newBody);
        }
        */
    }

    /**
     * Add a counter increment statement to the bodyStmt
     *
     * @param bodyStmt
     * @param isConstructor
     * @return New body statement if new body need to be set. Null otherwise.
     */
    @SuppressWarnings("unchecked")
    private Statement addUpdateStmt(Statement bodyStmt, boolean isConstructor, String fieldName) {
        AST ast = bodyStmt.getAST();

        // create a counter increment statement to be added
        ExpressionStatement exprStmt = createUpdateStmt(ast, fieldName, false);

        Block b;
        if (bodyStmt instanceof EmptyStatement) {
            // if body is empty, the counter increment statement will be a new body
            return exprStmt;
        } else if (bodyStmt instanceof Block) {
            // if body is a block, prepend the increment statement to it.
            b = (Block) bodyStmt;
            if (isConstructor) {
                if (b.statements().size() > 0) {
                    Statement stmt = (Statement) b.statements().get(0);
                    if (stmt instanceof SuperConstructorInvocation) {
                        b.statements().add(1, exprStmt);
                    } else {
                        b.statements().add(0, exprStmt);
                    }
                } else
                    b.statements().add(0, exprStmt);
            } else {
                b.statements().add(0, exprStmt);
            }
            // return null as the parent can use the same AST for its body
            return null;
        } else {
            // if the body is not a block, create a new block and
            // add the body statement to the block
            b = ast.newBlock();
            b.statements().add(ASTNode.copySubtree(ast, bodyStmt));
            b.statements().add(0, exprStmt);
            // return the new body so that the parent sets its body to this block
            return b;
        }
    }

    private void addUpdateStatement(Statement statement, String string) {
        addUpdateStatement(statement, string, false);
    }

    private void addUpdateStatement(Statement statement, String string, boolean isStatic) {
        ASTNode parentStatement = statement.getParent();
        ExpressionStatement update = null;
        update = createUpdateStmt(statement.getAST(), string, isStatic);

        if (parentStatement instanceof Block) {
            //System.out.println("in block");
            Block b = (Block) parentStatement;
            int index = b.statements().indexOf(statement);
            //System.out.println("parentStatement is at index "+index+" of grandpa statement");
            /*if(update.getParent() == null) {
                System.out.println("null parent");
            } else {
                System.out.println("not null parent");
            }*/
            b.statements().add(index, update);
        } else if (parentStatement instanceof IfStatement) {
            //System.out.println("In IfStatement");
            IfStatement ifStmt = (IfStatement) parentStatement;
            Statement thenStmt = ifStmt.getThenStatement();
            Statement elseStmt = ifStmt.getElseStatement();
            if (thenStmt.equals(statement)) {
                //System.out.println("the Then statement is the one we want");
                Block newBlock = statement.getAST().newBlock();
                newBlock.statements().add(update);
                newBlock.statements().add(ASTNode.copySubtree(statement.getAST(), thenStmt));
                ifStmt.setThenStatement(newBlock);
            } else if (elseStmt.equals(statement)) {
                //System.out.println("the Else statement is the one we want");
                Block newBlock = statement.getAST().newBlock();
                newBlock.statements().add(update);
                newBlock.statements().add(ASTNode.copySubtree(statement.getAST(), elseStmt));
                ifStmt.setElseStatement(newBlock);
            } else {
                //System.out.println("neither matches");
            }
            //System.out.println("if: "+thenStmt);
            //System.out.println("else: "+elseStmt);
        } else if (parentStatement instanceof ForStatement) {
            //System.out.println("In ForStatement");
            ForStatement forStmt = (ForStatement) parentStatement;
            Statement forBody = forStmt.getBody();
            //System.out.println("forBody: "+forBody);
            //System.out.println("the Else statement is the one we want");
            Block newBlock = statement.getAST().newBlock();
            newBlock.statements().add(update);
            newBlock.statements().add(ASTNode.copySubtree(statement.getAST(), forBody));
            forStmt.setBody(newBlock);
        } else if (parentStatement instanceof DoStatement) {
            //System.out.println("In ForStatement");
            DoStatement doStmt = (DoStatement) parentStatement;
            Statement doBody = doStmt.getBody();
            //System.out.println("forBody: "+forBody);
            //System.out.println("the Else statement is the one we want");
            Block newBlock = statement.getAST().newBlock();
            newBlock.statements().add(update);
            newBlock.statements().add(ASTNode.copySubtree(statement.getAST(), doBody));
            doStmt.setBody(newBlock);
        } else if (parentStatement instanceof SwitchStatement) {
            //System.out.println("In SwitchStatement");
            SwitchStatement switchStmt = (SwitchStatement) parentStatement;
            int index = switchStmt.statements().indexOf(statement);
            switchStmt.statements().add(index, update);
            //System.out.println(switchStmt.getExpression());
        } else if (parentStatement instanceof SwitchCase) {
            ////System.out.println("In SwitchCase");
            SwitchCase switchCase = (SwitchCase) parentStatement;
            ////System.out.println(switchCase.getExpression());
        } else {
            //System.out.println("In something else");
        }
    }
}
