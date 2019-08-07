package mantis.ftinst;

import org.eclipse.jdt.core.dom.*;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Stack;

/**
 * Inserts loop counters
 */
public class LoopVisitor extends ASTVisitor {

    protected static int unique_id = -1;
    // stores all loop counters
    protected static LinkedList<String> globalList = new LinkedList<String>();
    protected String currentClassName = "";
    protected Stack<String> classScopes = new Stack<String>();

    private String getFullClassName() {
        ListIterator<String> iter = classScopes.listIterator();
        String fullname = "";
        if (iter.hasNext())
            fullname = (String) iter.next();
        else
            return fullname;
        while (iter.hasNext()) {
            fullname = fullname + "SSS" + (String) iter.next();
        }
        return fullname;
    }

    private String getNextCounterName() {
        ++unique_id;
        return "mantisl_" + getFullClassName() + "_" + unique_id;
    }

    // Class declaration
    public boolean visit(TypeDeclaration typeDec) {
        currentClassName = typeDec.getName().getIdentifier();
        classScopes.push(currentClassName);
        return true;
    }

    public void endVisit(TypeDeclaration typeDec) {
        classScopes.pop();
        if (classScopes.isEmpty()) {
            currentClassName = "";
        } else {
            currentClassName = classScopes.peek();
        }
    }

    public void endVisit(WhileStatement whileStmt) {
        Statement newBody = addCounterIncrStmt(whileStmt.getBody());
        if (newBody != null) {
            whileStmt.setBody(newBody);
        }
    }

    public void endVisit(DoStatement doStmt) {
        Statement newBody = addCounterIncrStmt(doStmt.getBody());
        if (newBody != null) {
            doStmt.setBody(newBody);
        }
    }

    public void endVisit(ForStatement forStmt) {
        Statement newBody = addCounterIncrStmt(forStmt.getBody());
        if (newBody != null) {
            forStmt.setBody(newBody);
        }
    }

    public void endVisit(EnhancedForStatement enhancedForStmt) {
        Statement newBody = addCounterIncrStmt(enhancedForStmt.getBody());
        if (newBody != null) {
            enhancedForStmt.setBody(newBody);
        }

    }

    /**
     * Add a counter increment statement to the bodyStmt
     *
     * @param bodyStmt
     * @return New body statement if new body need to be set. Null otherwise.
     */
    @SuppressWarnings("unchecked")
    private Statement addCounterIncrStmt(Statement bodyStmt) {
        AST ast = bodyStmt.getAST();

        // create a counter increment statement to be added
        ExpressionStatement exprStmt = createCounterIncrStmt(ast);

        Block b;
        if (bodyStmt instanceof EmptyStatement) {
            // if body is empty, the counter increment statement will be a new body
            return exprStmt;
        } else if (bodyStmt instanceof Block) {
            // if body is a block, prepend the increment statement to it.
            b = (Block) bodyStmt;
            b.statements().add(0, exprStmt);
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

    private ExpressionStatement createCounterIncrStmt(AST ast) {
        // create a new unique counter name
        String counterName = getNextCounterName();
        globalList.add(counterName);
        // Thread local counter access
        String code = Utils.getCounterAccessString() + counterName + "++;";
        Block b = Utils.createASTForStatements(code);
        return (ExpressionStatement) ASTNode.copySubtree(ast, (ExpressionStatement) (b.statements().get(0)));

    }

//    @SuppressWarnings("unchecked")
//    private void addFeatureCounter(Statement bodyStmt){
//        AST ast = bodyStmt.getAST();
//        ExpressionStatement exprStmt = createCounterIncrStmt(ast);
//        Block b;
//        
//        if(bodyStmt instanceof Block){
//            b = (Block)bodyStmt;
//        } else if(bodyStmt instanceof EmptyStatement){
//            // if the body is empty, replace the EmptyStatement with the increment statement
//            ASTNode parent = bodyStmt.getParent();
//            if( parent instanceof DoStatement){
//                DoStatement ds = (DoStatement)parent;
//                ds.setBody(exprStmt);
//            } else if (parent instanceof EnhancedForStatement){
//                EnhancedForStatement efs = (EnhancedForStatement)parent;
//                efs.setBody(exprStmt);
//            } else if (parent instanceof ForStatement){
//                ForStatement fs = (ForStatement)parent;
//                fs.setBody(exprStmt);
//            } else if (parent instanceof WhileStatement){
//                WhileStatement ws = (WhileStatement)parent;
//                ws.setBody(exprStmt);
//            }            
//            return;
//        } else {
//            // if the body is not a block, create a new block and add the body statement to the block
//            b = ast.newBlock();            
//            b.statements().add(0,ASTNode.copySubtree(ast,bodyStmt));
//            ASTNode parent = bodyStmt.getParent();
//            if( parent instanceof DoStatement){
//                DoStatement ds = (DoStatement)parent;
//                ds.setBody(b);
//            } else if (parent instanceof EnhancedForStatement){
//                EnhancedForStatement efs = (EnhancedForStatement)parent;
//                efs.setBody(b);
//            } else if (parent instanceof ForStatement){
//                ForStatement fs = (ForStatement)parent;
//                fs.setBody(b);
//            } else if (parent instanceof WhileStatement){
//                WhileStatement ws = (WhileStatement)parent;
//                ws.setBody(b);
//            }
//        }
//        
//        b.statements().add(0, ASTNode.copySubtree(ast,exprStmt));
//        
//    }

//  /**
//  * Create an AST node corresponds to getting thread local MatisInstObj instance
//  * (i.e. ((MantisInstObj)Mantis.inst.get()) )
//  * @param ast
//  * @param fieldname
//  * @return
//  */
// private FieldAccess createMantisInstFieldAccess(AST ast, String fieldname){
//     
//     FieldAccess fieldAccess = ast.newFieldAccess();
//     // setup a field name (unique counter name)
//     fieldAccess.setName(ast.newSimpleName(fieldname));
//     ParenthesizedExpression parenExpr = ast.newParenthesizedExpression();
//     CastExpression castExpr = ast.newCastExpression();
//     castExpr.setType(ast.newSimpleType(ast.newSimpleName("MantisInstObj")));
//     MethodInvocation methodInvoc = ast.newMethodInvocation();
//     methodInvoc.setName(ast.newSimpleName("get"));
//     methodInvoc.setExpression(ast.newName("Mantis.inst"));
//     castExpr.setExpression(methodInvoc);
//     parenExpr.setExpression(castExpr);
//     fieldAccess.setExpression(parenExpr);
//     
//     return fieldAccess;      
// }

//    private ExpressionStatement createCounterIncrStmt(AST ast){
//        // create a new unique counter name
//        String counterName = getNextCounterName(); 
//        globalList.add(counterName);
//        System.out.println("Counter added : " + counterName);
//        
//        // create an statement for counter increment
//        PostfixExpression postfixExpr = ast.newPostfixExpression();
//        postfixExpr.setOperand(createMantisInstFieldAccess(ast, counterName));
//        postfixExpr.setOperator(Operator.toOperator("++"));
//        ExpressionStatement exprStmt = ast.newExpressionStatement(postfixExpr);
//        
//        return exprStmt;
//    }

}
