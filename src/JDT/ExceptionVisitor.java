package mantis.ftinst;

import org.eclipse.jdt.core.dom.*;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Stack;

public class ExceptionVisitor extends ASTVisitor {
    protected static int unique_id = -1;
    // stores all method counters
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

    private String getNextCounterName() {
        ++unique_id;
        return "mantise_" + getFullClassName() + "_" + unique_id;
    }

    public void endVisit(CatchClause node) {
        Block body = node.getBody();
        Statement newBody = addCounterIncrStmt(body);
        if (newBody != null) {
            node.setBody((Block) newBody);
        }

    }

}
