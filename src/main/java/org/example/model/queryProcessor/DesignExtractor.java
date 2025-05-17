package org.example.model.queryProcessor;

import org.example.model.ast.TNode;
import org.example.model.enums.EntityType;

import java.util.*;

public class DesignExtractor {
    private final PKB pkb;
    private int currentStmtNumber = 1;
    private final Deque<Integer> parentStack = new ArrayDeque<>();
    private String currentProcedure;
    private final Map<Integer, Set<Integer>> cfg = new HashMap<>();
    private final Map<Integer, List<Runnable>> pendingAfterIfEnds = new HashMap<>();

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
        propagateCallModifies();
        propagateCallUses();
        extractNextRelations();
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
        Integer prev = null;

        while (stmt != null) {
            int curr = currentStmtNumber++;
            pkb.addStmt(curr, stmt.getType());

            /* 1. relacje Parent / Follows / Next */
            if (!parentStack.isEmpty()) pkb.setParent(parentStack.peek(), curr);
            if (prev != null) {
                pkb.setFollows(prev, curr);
                addNextEdge(prev, curr);
            }
            prev = curr;

            /* 2. jeśli ktoś czekał na numer „następnej” */
            List<Runnable> hooks = pendingAfterIfEnds.remove(curr - 1);
            if (hooks != null) hooks.forEach(Runnable::run);

            /* 3. obsługa konkretnego typu stmt */
            processStmt(stmt, curr);
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
        if (!parentStack.isEmpty()) {
            pkb.propagateModifiesToParent(stmtNumber, varName);
        }

        // Handle Uses (right side)
        TNode exprNode = varNode.getRightSibling();
        pkb.setAssignRhsTree(stmtNumber, exprNode);
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

    private void processWhile(TNode whileNode, int whileNr) {
        TNode cond = whileNode.getFirstChild();

        Set<String> condVars = extractVariablesFromNode(cond);

        pkb.setWhileControlVars(whileNr, condVars);

        for (String v : condVars) {
            pkb.addVariable(v);
            pkb.setUsesStmt(whileNr, v);

            if (currentProcedure != null) {
                pkb.setUsesProc(currentProcedure, v);
            }

            if (!parentStack.isEmpty()) {
                pkb.propagateUsesToParent(whileNr, v);
            }
        }

        parentStack.push(whileNr);

        TNode body = cond.getRightSibling();
        int firstInBody = currentStmtNumber;

        processStmtList(body);

        int lastInBody = currentStmtNumber - 1;
        parentStack.pop();

        addNextEdge(whileNr, firstInBody);
        addNextEdge(lastInBody, whileNr);
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
            if (!parentStack.isEmpty()) {
                pkb.propagateModifiesToParent(stmtNumber, var);
            }
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
    private void processIf(TNode ifNode, int ifStmtNr) {

        TNode cond = ifNode.getFirstChild();
        for (String v : extractVariablesFromCond(cond)) {
            pkb.addVariable(v);
            pkb.setUsesStmt(ifStmtNr, v);
            if (currentProcedure != null) pkb.setUsesProc(currentProcedure, v);
            if (!parentStack.isEmpty()) pkb.propagateUsesToParent(ifStmtNr, v);
        }

        parentStack.push(ifStmtNr);
        TNode thenList  = cond.getRightSibling();
        TNode elseList  = thenList.getRightSibling();

        int thenStart  = currentStmtNumber;
        processStmtList(thenList);
        int thenEnd    = currentStmtNumber - 1;

        int elseStart  = currentStmtNumber;
        processStmtList(elseList);
        int elseEnd    = currentStmtNumber - 1;

        parentStack.pop();

        addNextEdge(ifStmtNr, thenStart);
        addNextEdge(ifStmtNr, elseStart);


        pendingAfterIfEnds
                .computeIfAbsent(thenEnd, k -> new ArrayList<>())
                .add(() -> addNextEdge(thenEnd, currentStmtNumber));
        pendingAfterIfEnds
                .computeIfAbsent(elseEnd, k -> new ArrayList<>())
                .add(() -> addNextEdge(elseEnd, currentStmtNumber));
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

    private void propagateCallModifies() {
        // Najpierw przetwórz bezpośrednie wywołania
        for (Integer stmt : pkb.getAllCallStmts()) {
            String proc = pkb.getCalledProcByStmt(stmt);
            for (String var : pkb.getModifiedByProc(proc)) {
                pkb.setModifiesStmt(stmt, var);
                pkb.propagateModifiesToParent(stmt, var);
            }
        }

        // Następnie przetwórz wywołania rekurencyjnie
        boolean changed;
        do {
            changed = false;
            for (String caller : pkb.getAllProcedures()) {
                Set<String> calledProcs = pkb.getCallsStar(caller);
                for (String callee : calledProcs) {
                    Set<String> calleeModifies = pkb.getModifiedByProc(callee);
                    for (String var : calleeModifies) {
                        if (pkb.setModifiesProc(caller, var)) {
                            changed = true;
                        }
                    }
                }
            }
        } while (changed);
    }

    private void propagateCallUses() {
        for (Integer callStmt : pkb.getAllCallStmts()) {
            String callee = pkb.getCalledProcByStmt(callStmt);
            for (String var : pkb.getUsedByProc(callee)) {
                pkb.setUsesStmt(callStmt, var);
                pkb.propagateUsesToParent(callStmt, var);
            }
        }
    }

    private int getLastStatementIn(TNode stmtListNode) {
        TNode last = stmtListNode.getFirstChild();
        TNode current = last;
        while (current != null) {
            last = current;
            current = current.getRightSibling();
        }
        return currentStmtNumber - 1;
    }

    private void addNextEdge(int from, int to) {
        pkb.addNext(from, to);
        cfg.computeIfAbsent(from, k -> new HashSet<>()).add(to);
    }

    private void extractNextRelations() {
        for (Integer from : cfg.keySet()) {
            Set<Integer> visited = new HashSet<>();
            Deque<Integer> stack = new ArrayDeque<>();
            stack.push(from);
            while (!stack.isEmpty()) {
                int curr = stack.pop();
                for (int next : cfg.getOrDefault(curr, Set.of())) {
                    if (visited.add(next)) {
                        pkb.addNextStar(from, next);
                        stack.push(next);
                    }
                }
            }
        }
    }
}