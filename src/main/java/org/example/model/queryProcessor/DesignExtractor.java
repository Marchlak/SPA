package org.example.model.queryProcessor;

import org.example.model.ast.TNode;
import org.example.model.enums.EntityType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class DesignExtractor {
    private final PKB pkb;
    private int currentStmtNumber = 1;
    private final Deque<Integer> parentStack = new ArrayDeque<>();
    private String currentProcedure;

    public DesignExtractor(PKB pkb) {
        this.pkb = pkb;
    }

    public void extract(TNode programNode) {
        TNode procListNode = programNode.getFirstChild();
        TNode proc = procListNode.getFirstChild();
        while (proc != null) {
            processProcedure(proc);
            proc = proc.getRightSibling();
        }
    }

    private void processProcedure(TNode procNode) {
        currentProcedure = procNode.getAttr().replace("\"", "");
        pkb.addProcedure(currentProcedure);
        TNode stmtList = procNode.getFirstChild();
        processStmtList(stmtList);
        currentProcedure = null;
    }

    private void processStmtList(TNode stmtListNode) {
        TNode stmt = stmtListNode.getFirstChild();
        Integer prevStmt = null;
        while (stmt != null) {
            int currentStmt = currentStmtNumber++;
            pkb.addStmt(currentStmt, stmt.getType());
            if (!parentStack.isEmpty()) {
                pkb.setParent(parentStack.peek(), currentStmt);
            }
            if (prevStmt != null) {
                pkb.setFollows(prevStmt, currentStmt);
            }
            prevStmt = currentStmt;
            processStmt(stmt, currentStmt);
            stmt = stmt.getRightSibling();
        }
    }

    private void processStmt(TNode stmt, int stmtNumber) {
        switch (stmt.getType()) {
            case ASSIGN:
                processAssign(stmt, stmtNumber);
                break;
            case WHILE:
                processWhile(stmt, stmtNumber);
                break;
            case CALL:
                processCall(stmt, stmtNumber);
                break;
            case IF:
                processIf(stmt, stmtNumber);
                break;
            default:
                throw new IllegalArgumentException("Unexpected node type: " + stmt.getType());
        }
    }

    private void processAssign(TNode assignNode, int stmtNumber) {
        // Handle Modifies (left side)
        TNode varNode = assignNode.getFirstChild();
        String varName = varNode.getAttr();
        pkb.addVariable(varName);
        pkb.setModifiesStmt(stmtNumber, varName);
        if (currentProcedure != null) {
            pkb.setModifiesProc(currentProcedure, varName);
        }

        // Handle Uses (right side)
        TNode exprNode = varNode.getRightSibling();
        Set<String> usedVars = extractVariablesFromNode(exprNode);
        for (String usedVar : usedVars) {
            pkb.addVariable(usedVar);
            pkb.setUsesStmt(stmtNumber, usedVar);
            if (currentProcedure != null) {
                pkb.setUsesProc(currentProcedure, usedVar);
            }
            // Propagate to parent containers
            if (!parentStack.isEmpty()) {
                pkb.propagateUsesToParent(stmtNumber, usedVar);
            }
        }
    }

    private void processWhile(TNode whileNode, int stmtNumber) {
        // Handle control variable (Uses)
        TNode cond = whileNode.getFirstChild();
        Set<String> condVars = extractVariablesFromNode(cond);
        for (String var : condVars) {
            pkb.addVariable(var);
            pkb.setUsesStmt(stmtNumber, var);
            if (currentProcedure != null) {
                pkb.setUsesProc(currentProcedure, var);
            }
            // Propagate to parent containers
            if (!parentStack.isEmpty()) {
                pkb.propagateUsesToParent(stmtNumber, var);
            }
        }

        parentStack.push(stmtNumber);
        TNode stmtList = cond.getRightSibling();
        processStmtList(stmtList);
        parentStack.pop();
    }

    private void processCall(TNode callNode, int stmtNumber) {
        TNode procNameNode = callNode.getFirstChild();
        String calledProc = procNameNode.getAttr().replace("\"", "");

        if (currentProcedure != null) {
            pkb.setCalls(currentProcedure, calledProc);
        }

        pkb.setCallStmt(stmtNumber, calledProc);

        if (!pkb.getCallsMap().containsKey(calledProc)) {
            pkb.addProcedure(calledProc);
        }

        Set<String> modifies = pkb.getModifiedByProc(calledProc);
        modifies.forEach(var -> {
            pkb.setModifiesProc(currentProcedure, var);
            pkb.setModifiesStmt(stmtNumber, var);
        });

        Set<String> uses = pkb.getUsedByProc(calledProc);
        uses.forEach(var -> {
            pkb.setUsesProc(currentProcedure, var);
            pkb.setUsesStmt(stmtNumber, var);
            if (!parentStack.isEmpty()) {
                pkb.propagateUsesToParent(stmtNumber, var);
            }
        });
    }
    private void processIf(TNode ifNode, int stmtNumber) {
        // Handle control variable (Uses)
        TNode cond = ifNode.getFirstChild();
        Set<String> condVars = extractVariablesFromCond(cond);
        for (String var : condVars) {
            pkb.addVariable(var);
            pkb.setUsesStmt(stmtNumber, var);
            if (currentProcedure != null) {
                pkb.setUsesProc(currentProcedure, var);
            }
            // Propagate to parent containers
            if (!parentStack.isEmpty()) {
                pkb.propagateUsesToParent(stmtNumber, var);
            }
        }

        parentStack.push(stmtNumber);
        TNode thenStmtList = cond.getRightSibling();
        TNode elseStmtList = thenStmtList.getRightSibling();
        processStmtList(thenStmtList);
        processStmtList(elseStmtList);
        parentStack.pop();
    }

    private Set<String> extractVariablesFromNode(TNode node) {
        Set<String> variables = new HashSet<>();
        if (node == null) {
            return variables;
        }

        // Check if current node is a variable
        if (node.getType() == EntityType.VARIABLE) {
            variables.add(node.getAttr());
        }

        // Recursively check children
        TNode child = node.getFirstChild();
        while (child != null) {
            variables.addAll(extractVariablesFromNode(child));
            child = child.getRightSibling();
        }

        return variables;
    }

    private Set<String> extractVariablesFromExpr(TNode exprNode) {
        Set<String> variables = new HashSet<>();
        Deque<TNode> stack = new ArrayDeque<>();
        stack.push(exprNode);

        while (!stack.isEmpty()) {
            TNode current = stack.pop();
            if (current.getType() == EntityType.VARIABLE) {
                variables.add(current.getAttr());
            }
            // Add children to stack (right to left for correct order)
            TNode child = current.getRightSibling();
            while (child != null) {
                stack.push(child);
                child = child.getRightSibling();
            }
        }
        return variables;
    }

    private Set<String> extractVariablesFromCond(TNode condNode) {
        // For simplicity, assuming conditions are similar to expressions
        return extractVariablesFromExpr(condNode);
    }


}
