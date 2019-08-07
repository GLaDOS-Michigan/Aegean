package mantis.ftinst;

import org.eclipse.jdt.core.dom.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

/**
 * Adds variables value tracking code
 */
public class VarValueVisitor extends ASTVisitor {
    // Key string used to mark ASTNodes that are created by instrumentation
    final protected static String mantisInstKey = "matis_inst";
    // number of versions of variable values to track
    final protected static int CUTOFF = 5;
    // map from variable's unique name to type name
    protected static HashMap<String, String> mapVarToType = new HashMap<String, String>();

    public boolean visit(CompilationUnit cu) {

        return true;
    }

    /**
     * Handles assignment statements enclosed by a block
     */
    @SuppressWarnings("unchecked")
    public void endVisit(Block b) {
        // if this block is an artifact of instrumentation, skip this block 
        if (b.getProperty(mantisInstKey) != null) {
            return;
        }
        List l = b.statements();
        for (int i = 0; i < l.size(); i++) {
            Statement stmt = (Statement) l.get(i);
            if (stmt.getNodeType() == Statement.EXPRESSION_STATEMENT) {
                ExpressionStatement exprStmt = (ExpressionStatement) stmt;
                Expression expr = exprStmt.getExpression();

                HashMap<Expression, ITypeBinding> map = new HashMap<Expression, ITypeBinding>();

                switch (expr.getNodeType()) {
                    case ASTNode.POSTFIX_EXPRESSION:
                        PostfixExpression postfixExpr = (PostfixExpression) expr;
                        map.put(postfixExpr.getOperand(), postfixExpr.resolveTypeBinding());
                        break;
                    case ASTNode.PREFIX_EXPRESSION:
                        PrefixExpression prefixExpr = (PrefixExpression) expr;
                        map.put(prefixExpr.getOperand(), prefixExpr.resolveTypeBinding());
                        break;
                    case ASTNode.ASSIGNMENT:
                        Assignment assignment = (Assignment) expr;
                        map.put(assignment.getLeftHandSide(), assignment.resolveTypeBinding());
                        // if right side is an assignment itself
                        while (assignment.getRightHandSide().getNodeType() == ASTNode.ASSIGNMENT) {
                            assignment = (Assignment) assignment.getRightHandSide();
                            map.put(assignment.getLeftHandSide(), assignment.resolveTypeBinding());
                        }
                        break;
                    default:
                        continue;
                }

                for (Entry<Expression, ITypeBinding> e : map.entrySet()) {

                    Expression left = e.getKey();
                    ITypeBinding typeBinding = e.getValue();

                    if (typeBinding == null) {
                        System.out.println("### NO TYPE BINDING B : " + expr);
                        //					continue;
                        System.exit(-1);
                        continue;
                    }

                    // Handles only primitive types
                    if (typeBinding.isPrimitive()) {
                        IVariableBinding vb = null;
                        if (left instanceof SimpleName) {
                            vb = (IVariableBinding) ((SimpleName) left).resolveBinding();
                        } else if (left instanceof FieldAccess) {
                            vb = ((FieldAccess) left).resolveFieldBinding();
                        } else {
                            // currently we don't handle array elements
                            continue;
                        }
                        // we don't track final variables
                        if (Modifier.isFinal(vb.getModifiers())) {
                            continue;
                        }
                        // create a unique name for this variable using binding key
                        String uniqueVarName = VarValueVisitor.getUniqueMantisVarName(vb.getKey());
                        // if this is first occurrence, put into the unique name to type map
                        if (!VarValueVisitor.mapVarToType.containsKey(uniqueVarName)) {
                            VarValueVisitor.mapVarToType.put(uniqueVarName, vb.getType().getName());
                        }
                        Block newBlock = getVarValueTrackingStmts(uniqueVarName, left);
                        // insert newly created statements to the enclosing block
                        Iterator iter = newBlock.statements().iterator();
                        while (iter.hasNext()) {
                            i++; // increment index to skip this statement
                            ASTNode newnode = ASTNode.copySubtree(left.getAST(), (ASTNode) iter.next());
                            // mark this AST node so that we don't add branch counter for this
                            newnode.setProperty(mantisInstKey, Boolean.valueOf(true));
                            l.add(i, newnode);
                        }
                    }

                } // end inner for

            } // end if node_type == expression_stmt

        } // end outer for
    }

    /**
     * Handles an assignment statement that are not enclosed by a block
     */
    public void endVisit(ExpressionStatement exprStmt) {

        Statement parent = (Statement) exprStmt.getParent();
        StructuralPropertyDescriptor spd = exprStmt.getLocationInParent();
        if (!spd.isChildProperty()) {
            // Block is handled by endVisit(Block)
            return;
        }

        Expression expr = exprStmt.getExpression();
        HashMap<Expression, ITypeBinding> map = new HashMap<Expression, ITypeBinding>();


        switch (expr.getNodeType()) {
            case ASTNode.POSTFIX_EXPRESSION:
                PostfixExpression postfixExpr = (PostfixExpression) expr;
                map.put(postfixExpr.getOperand(), postfixExpr.resolveTypeBinding());
                break;
            case ASTNode.PREFIX_EXPRESSION:
                PrefixExpression prefixExpr = (PrefixExpression) expr;
                map.put(prefixExpr.getOperand(), prefixExpr.resolveTypeBinding());
                break;
            case ASTNode.ASSIGNMENT:
                Assignment assignment = (Assignment) expr;
                map.put(assignment.getLeftHandSide(), assignment.resolveTypeBinding());
                // if right side is another assignment itself
                while (assignment.getRightHandSide().getNodeType() == ASTNode.ASSIGNMENT) {
                    assignment = (Assignment) assignment.getRightHandSide();
                    map.put(assignment.getLeftHandSide(), assignment.resolveTypeBinding());
                }
                break;
            default:
                return;
        }

        String code = exprStmt.toString() + "\n";
        int numVarProcessed = 0;
        for (Entry<Expression, ITypeBinding> e : map.entrySet()) {
            Expression left = e.getKey();
            ITypeBinding typeBinding = e.getValue();

            if (typeBinding == null) {
                System.out.println("### NO TYPE BINDING A : " + expr);
                //			return;
                System.exit(-1);
                return;
            }

            if (typeBinding.isPrimitive()) {
                if (expr.getLocationInParent() == null) {
                    System.out.println("No location info : " + exprStmt);
                    System.exit(-1);
                    return;
                }
                IVariableBinding vb = null;
                if (left instanceof SimpleName) {
                    vb = (IVariableBinding) ((SimpleName) left).resolveBinding();
                } else if (left instanceof FieldAccess) {
                    vb = ((FieldAccess) left).resolveFieldBinding();
                } else {
                    // currently we don't handle array elements
                    continue;
                }
                // we don't track final variables
                if (Modifier.isFinal(vb.getModifiers())) {
                    continue;
                }
                // create a unique name for this variable using binding key
                String uniqueVarName = VarValueVisitor.getUniqueMantisVarName(vb.getKey());
                // if this is first occurrence, put into the unique name to type map
                if (!VarValueVisitor.mapVarToType.containsKey(uniqueVarName)) {
                    VarValueVisitor.mapVarToType.put(uniqueVarName, vb.getType().getName());
                }


                // create a block that contains the original assignment and value tracking statements 
                code += getVarValueTrackingString(uniqueVarName, left);

                numVarProcessed++;
            }

        } // end for

        if (numVarProcessed > 0) {
            Block b = Utils.createASTForStatements(code);
            b = (Block) ASTNode.copySubtree(parent.getAST(), b);
            // mark this block not to get processed by endVisit(Block)
            b.setProperty(mantisInstKey, Boolean.valueOf(true));
            // mark value tracking statement (ifStatement) not to get processed for branch counting 
            for (int i = 1; i < b.statements().size(); i++) {
                Statement stmt = (Statement) b.statements().get(i);
                stmt.setProperty(mantisInstKey, Boolean.valueOf(true));
            }
            // set this block as a new child of the parent
            parent.setStructuralProperty(spd, b);
        }

    }

    private String getVarValueTrackingString(String uniqueVarName, ASTNode var) {

        String varTackingAccess = Utils.getVarTrackingAccessString();
        String code;

        String type = mapVarToType.get(uniqueVarName);

        if (Config.UseThreadLocal || !type.equals("boolean")) {
            code = "if( " + varTackingAccess + uniqueVarName + " < "
                    + CUTOFF + " ) {" + varTackingAccess + uniqueVarName + "_data["
                    + varTackingAccess + uniqueVarName + "] = " + var + ";\n";
        } else {
            code = "if( " + varTackingAccess + uniqueVarName + " < "
                    + CUTOFF + " ) {" + varTackingAccess + uniqueVarName + "_data["
                    + varTackingAccess + uniqueVarName + "] = (" + var + "?1:0);\n";
        }
        code += "++" + varTackingAccess + uniqueVarName + ";\n}\n";
        return code;
    }

    private Block getVarValueTrackingStmts(String uniqueVarName, ASTNode left) {
        String code = getVarValueTrackingString(uniqueVarName, left);
        Block b = Utils.createASTForStatements(code);
        return b;
    }

    /**
     * Create a unique field name, which will be added to Mantis classes, using a binding key string
     * of a variable we are tracking
     *
     * @param varBindingKey binding key string
     * @return
     */
    private static String getUniqueMantisVarName(String varBindingKey) {
        String ret = varBindingKey;
        // replace all characters that cannot be used as a field name
        ret = ret.replace("(", "_LP_"); // method
        ret = ret.replace("[", "_LBP_");
        ret = ret.replace(")", "_RP_");
        ret = ret.replace("/", "_pp_"); // package
        ret = ret.replace("$", "_II_"); // inner class
        ret = ret.replace(".", "_FF_"); // Field
        ret = ret.replace("#", "_BB_"); // block
        ret = ret.replace(";", "_SC_");
        ret = ret.replace("|", "_OO_");
        ret = ret.replace(":", "_CC_");
        ret = ret.replace("<", "_LT_");
        ret = ret.replace(">", "_GT_");
        ret = ret.replace("-", "_MM_");
        ret = ret.replace("+", "_PP_");
        return "mantisv_" + ret;
    }

}
