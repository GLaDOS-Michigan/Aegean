package mantis.ftinst;

import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.ListIterator;


public class Utils {

    private static ASTParser parser = ASTParser.newParser(AST.JLS3);

    protected static String getCounterAccessString() {
        if (Config.UseThreadLocal) {
            return "((MantisInstObj) Mantis.inst.get()).";
        } else {
            // Use static fields of Mantis class
            return "Mantis.";
        }
    }

    protected static String getVarTrackingAccessString() {
        if (Config.UseThreadLocal) {
            return "((MantisInstVarObj) Mantis.instVar.get()).";
        } else {
            // Use static fields of Mantis class
            return "Mantis.";
        }
    }

    /**
     * utility method used to create ASTes for a sequence of statements
     *
     * @param strStmts String of statements to be parsed
     * @return Block whose statements are the new AST(es)
     */
    public static Block createASTForStatements(String strStmts) {
        Utils.parser.setKind(ASTParser.K_STATEMENTS);
        Utils.parser.setSource(strStmts.toCharArray());
        Block ret = (Block) Utils.parser.createAST(null);
        if (ret.statements().size() == 0) {
            System.err.println("Failed to parsing the following code : " + strStmts);
            (new Throwable()).printStackTrace();
            System.exit(-1);
        }
        return ret;
    }

    /**
     * utility method used to create ASTes for class body declarations
     *
     * @param strStmts
     * @return TypeDeclaraioin whose BodyDeclarations are new ASTes
     */
    protected static TypeDeclaration createASTForClassBodyDecl(String strStmts) {
        Utils.parser.setKind(ASTParser.K_CLASS_BODY_DECLARATIONS);
        Utils.parser.setSource(strStmts.toCharArray());
        ASTNode astnode = Utils.parser.createAST(null);
        if (!(astnode instanceof TypeDeclaration)) {
            System.err.println("Failed to parsing the following code : " + strStmts);
            (new Throwable()).printStackTrace();
            System.exit(-1);
        }
        return (TypeDeclaration) astnode;
    }

    /**
     * Prepend the elements of the given list to the body block
     *
     * @param body
     * @param list
     * @return resulting block
     */
    @SuppressWarnings({"unchecked"})
    protected static Block prependASTNodes(Block body, List list) {
        prependASTNodes(body.getAST(), body.statements(), list);
        return body;
    }

    @SuppressWarnings("unchecked")
    protected static void prependASTNodes(AST ast, List target, List srcList) {
        ListIterator iter = srcList.listIterator(srcList.size());
        while (iter.hasPrevious()) {
            target.add(0, ASTNode.copySubtree(ast, (ASTNode) iter.previous()));
        }
    }

    /**
     * Append the elements of the given list to the body block
     *
     * @param body
     * @param list
     * @return resulting block
     */
    @SuppressWarnings({"unchecked"})
    protected static Block appendASTNodes(Block body, List list) {
        appendASTNodes(body.getAST(), body.statements(), list);
        return body;
    }

    @SuppressWarnings("unchecked")
    protected static void appendASTNodes(AST ast, List target, List srcList) {
        for (Object node : srcList) {
            target.add(ASTNode.copySubtree(ast, (ASTNode) node));
        }
    }

}
