package mantis.ftinst;

import org.eclipse.jdt.core.dom.*;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Stack;

/**
 * Inserts branch counters
 */
public class BranchVisitor extends ASTVisitor {

    protected static int unique_id = -1;
    // stores all branch counters
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
        return "mantisb_" + getFullClassName() + "_" + unique_id;
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

    public void endVisit(IfStatement ifStmt) {

        if (ifStmt.getProperty(VarValueVisitor.mantisInstKey) != null) {
            return;
        }

        Statement newBody = addCounterIncrStmt(ifStmt.getThenStatement());
        if (newBody != null) {
            ifStmt.setThenStatement(newBody);
        }

        Statement elseStmt = ifStmt.getElseStatement();
        if (elseStmt != null && !(elseStmt instanceof IfStatement)) {
            newBody = addCounterIncrStmt(elseStmt);
            if (newBody != null) {
                ifStmt.setElseStatement(newBody);
            }
        }

    }

    public void endVisit(SwitchStatement switchStmt) {
        List l = switchStmt.statements();
        for (int i = 0; i < l.size(); i++) {
            ASTNode node = (ASTNode) l.get(i);
            if (node.getNodeType() == ASTNode.SWITCH_CASE) {
                if (((ASTNode) l.get(i + 1)).getNodeType() != ASTNode.SWITCH_CASE) {
                    ExpressionStatement es = createCounterIncrStmt(node.getAST());
                    i++;
                    l.add(i, es);
                }
            }
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
//        String code = "++((MantisInstObj) Mantis.inst.get())." + counterName+";";
//        Block b = Utils.createASTForStatements(code);
//        return (ExpressionStatement) ASTNode.copySubtree(ast, (ExpressionStatement)(b.statements().get(0)));
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
