package org.example.model.queryProcessor;

import org.example.model.ast.TNode;
import org.example.model.enums.EntityType;

import java.util.*;
import java.util.stream.Collectors;

public class DesignExtractor {
    private final PKB pkb;
    private int currentStmtNumber = 1;
    private final Deque<Integer> parentStack = new ArrayDeque<>();
    private String currentProcedure;
    private final Map<Integer, Set<Integer>> cfg = new HashMap<>();
    private final Map<Integer, List<Runnable>> pendingAfterIfEnds = new HashMap<>();
    private final Map<Integer, List<Runnable>> pendingAfterLoops = new HashMap<>();
    private final Map<TNode,Integer> nodeToStmt = new HashMap<>();

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
        extractAffectsRelations();
    }

    private void processProcedure(TNode procNode) {
        currentProcedure = procNode.getAttr().replace("\"", "");
        pkb.addProcedure(currentProcedure);

        TNode stmtList = procNode.getFirstChild();
        processStmtList(stmtList);


        pendingAfterIfEnds.clear();
        pendingAfterLoops.clear();
        pendingWhileFalse.clear();

        parentStack.clear();
        currentProcedure = null;
    }



    private void extractAffectsRelations() {
        for (String proc : pkb.getAllProcedures()) {
            Set<Integer> stmts = pkb.getAllStmts().stream()
                    .filter(s -> proc.equals(pkb.getProcedureOfStmt(s)))
                    .collect(Collectors.toSet());
            for (int a1 : stmts) {
                if (pkb.getEntityType(a1) != EntityType.ASSIGN) continue;
                Set<String> mods = pkb.getModifiedByStmt(a1);
                if (mods.size() != 1) continue;
                String v = mods.iterator().next();
                Deque<Integer> dq = new ArrayDeque<>(pkb.getNext(a1));
                Set<Integer> seen = new HashSet<>();
                while (!dq.isEmpty()) {
                    int cur = dq.pop();
                    if (!seen.add(cur)) continue;

                    if (pkb.getUsedByStmt(cur).contains(v) &&
                            pkb.getEntityType(cur) == EntityType.ASSIGN)
                        pkb.addAffects(a1, cur);

                    EntityType et = pkb.getEntityType(cur);
                    boolean redefines = (et == EntityType.ASSIGN || et == EntityType.CALL)
                            && pkb.getModifiedByStmt(cur).contains(v);
                    if (redefines) continue;

                    dq.addAll(pkb.getNext(cur));
                }
            }
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
        TNode varNode = assignNode.getFirstChild();
        String varName = varNode.getAttr();
        pkb.addVariable(varName);
        pkb.setAssignLhs(stmtNumber, varName);
        pkb.setModifiesStmt(stmtNumber, varName);
        if (currentProcedure != null) {
            pkb.setModifiesProc(currentProcedure, varName);
        }
        if (!parentStack.isEmpty()) {
            pkb.propagateModifiesToParent(stmtNumber, varName);
        }

        TNode exprNode = varNode.getRightSibling();
        pkb.setAssignRhsTree(stmtNumber, exprNode);
        collectConstants(exprNode);
        Set<String> usedVars = extractVariablesFromNode(exprNode);
        for (String usedVar : usedVars) {
            pkb.addVariable(usedVar);
            pkb.setUsesStmt(stmtNumber, usedVar);
            if (currentProcedure != null) {
                pkb.setUsesProc(currentProcedure, usedVar);
            }
            if (!parentStack.isEmpty()) {
                pkb.propagateUsesToParent(stmtNumber, usedVar);
            }
        }
    }


    private final List<Integer> pendingWhileFalse = new ArrayList<>();

    private void processStmtList(TNode stmtListNode) {
        TNode stmt = stmtListNode.getFirstChild();
        Integer prev = null;
        while (stmt != null) {
            int curr = currentStmtNumber++;
            for (int h : pendingWhileFalse) addNextEdge(h, curr);
            pendingWhileFalse.clear();

            pkb.addStmt(curr, stmt.getType());
            pkb.setStmtProcedure(curr, currentProcedure);
            nodeToStmt.put(stmt, curr);
            if (!parentStack.isEmpty()) pkb.setParent(parentStack.peek(), curr);

            if (prev != null) {
                pkb.setFollows(prev, curr);

                EntityType prevType = pkb.getEntityType(prev);
                if (prevType != EntityType.IF && prevType != EntityType.WHILE) {
                    addNextEdge(prev, curr);
                }
            }
            prev = curr;

            List<Runnable> hooks = pendingAfterIfEnds.remove(curr - 1);
            if (hooks != null) hooks.forEach(Runnable::run);

            hooks = pendingAfterLoops.remove(curr - 1);
            if (hooks != null) hooks.forEach(Runnable::run);

            processStmt(stmt, curr);
            stmt = stmt.getRightSibling();
        }

        if (prev != null) {
            List<Runnable> loopHooks = pendingAfterLoops.remove(prev);
            if (loopHooks != null) loopHooks.forEach(Runnable::run);
        }
    }

    private void processWhile(TNode whileNode, int whileNr) {
        TNode cond = whileNode.getFirstChild();
        collectConstants(cond);
        Set<String> cv = extractVariablesFromNode(cond);
        pkb.setWhileControlVars(whileNr, cv);
        for (String v : cv) {
            pkb.addVariable(v);
            pkb.setUsesStmt(whileNr, v);
            if (currentProcedure != null) pkb.setUsesProc(currentProcedure, v);
            if (!parentStack.isEmpty()) pkb.propagateUsesToParent(whileNr, v);
        }

        parentStack.push(whileNr);
        TNode body = cond.getRightSibling();

        int firstInBody = currentStmtNumber;
        processStmtList(body);

        int lastInBody = currentStmtNumber - 1;
        int lastTopLevel = -1;
        for (int i = lastInBody; i >= firstInBody; i--) {
            Integer p = pkb.getParentMap().get(i);
            if (p != null && p == whileNr) { lastTopLevel = i; break; }
        }
        if (lastTopLevel == -1) lastTopLevel = lastInBody;

        parentStack.pop();

        addNextEdge(whileNr, firstInBody);

        EntityType topType = pkb.getEntityType(lastTopLevel);
        if (topType != EntityType.IF && topType != EntityType.WHILE) {
            addNextEdge(lastTopLevel, whileNr);
        }

        final int nextNum = currentStmtNumber;
        pendingAfterLoops.computeIfAbsent(lastInBody, k -> new ArrayList<>())
                .add(() -> { if (nextNum < currentStmtNumber) addNextEdge(whileNr, nextNum); });

        TNode anc = whileNode.getParent();
        TNode enclosingWhileNode = null;
        while (anc != null && anc.getType() != EntityType.PROCEDURE) {
            if (anc.getType() == EntityType.WHILE) { enclosingWhileNode = anc; break; }
            if (anc.getRightSibling() != null) { enclosingWhileNode = null; break; }
            anc = anc.getParent();
        }
        if (enclosingWhileNode != null && lastTillAncestor(whileNode, enclosingWhileNode)) {
            Integer enclosingNr = nodeToStmt.get(enclosingWhileNode);
            if (enclosingNr != null) addNextEdge(whileNr, enclosingNr);
        }
    }


    private boolean lastTillAncestor(TNode node, TNode ancestor) {
        TNode cur = node;
        while (cur != null && cur != ancestor) {
            if (cur.getRightSibling() != null) return false;
            cur = cur.getParent();
        }
        return cur == ancestor;
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

    private boolean isStmtNode(EntityType t) {
        return t == EntityType.ASSIGN
                || t == EntityType.CALL
                || t == EntityType.IF
                || t == EntityType.WHILE;
    }

    private boolean hasNextStmtSibling(TNode n) {
        TNode cur = n;
        while (true) {
            TNode sib = cur.getRightSibling();
            if (sib != null && isStmtNode(sib.getType())) return true;
            TNode list = cur.getParent();
            if (list == null) return false;
            TNode owner = list.getParent();
            if (owner == null || owner.getType() != EntityType.IF) return false;
            cur = owner;
        }
    }

    private void processIf(TNode ifNode, int ifStmtNr) {
        TNode cond = ifNode.getFirstChild();
        collectConstants(cond);
        Set<String> cv = extractVariablesFromCond(cond);
        pkb.setIfControlVars(ifStmtNr, cv);
        for (String v : cv) {
            pkb.addVariable(v);
            pkb.setUsesStmt(ifStmtNr, v);
            if (currentProcedure != null) pkb.setUsesProc(currentProcedure, v);
            if (!parentStack.isEmpty()) pkb.propagateUsesToParent(ifStmtNr, v);
        }

        parentStack.push(ifStmtNr);
        TNode thenList = cond.getRightSibling();
        TNode elseList = thenList.getRightSibling();

        int thenStart = currentStmtNumber;
        processStmtList(thenList);
        int thenEnd = currentStmtNumber - 1;

        int elseStart = currentStmtNumber;
        processStmtList(elseList);
        int elseEnd = currentStmtNumber - 1;

        parentStack.pop();

        addNextEdge(ifStmtNr, thenStart);
        addNextEdge(ifStmtNr, elseStart);

        if (hasNextStmtSibling(ifNode)) {
            final int nextNum = currentStmtNumber;
            pendingAfterIfEnds
                    .computeIfAbsent(elseEnd, k -> new ArrayList<>())
                    .add(() -> {
                        addNextEdge(thenEnd, nextNum);
                        addNextEdge(elseEnd, nextNum);
                    });
        } else {
            Integer enclosingWhile = null;
            for (Integer anc : parentStack) {
                if (pkb.getEntityType(anc) == EntityType.WHILE) {
                    enclosingWhile = anc;
                    break;
                }
            }
            if (enclosingWhile != null) {
                addNextEdge(thenEnd, enclosingWhile);
                addNextEdge(elseEnd, enclosingWhile);
            }
        }
    }

    private Set<String> extractVariablesFromNode(TNode node) {
        Set<String> variables = new HashSet<>();
        if (node == null) {
            return variables;
        }

        if (node.getType() == EntityType.VARIABLE) {
            variables.add(node.getAttr());
        }

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
            TNode child = current.getRightSibling();
            while (child != null) {
                stack.push(child);
                child = child.getRightSibling();
            }
        }
        return variables;
    }

    private Set<String> extractVariablesFromCond(TNode condNode) {
        return extractVariablesFromExpr(condNode);
    }

    private void propagateCallModifies() {
        boolean changed;
        do {
            changed = false;
            for (String caller : pkb.getAllProcedures()) {
                for (String callee : pkb.getCallsStar(caller)) {
                    for (String var : pkb.getModifiedByProc(callee)) {
                        if (pkb.setModifiesProc(caller, var)) {
                            changed = true;
                        }
                    }
                }
            }
        } while (changed);
        for (Integer stmt : pkb.getAllCallStmts()) {
            String proc = pkb.getCalledProcByStmt(stmt);
            for (String var : pkb.getModifiedByProc(proc)) {
                pkb.setModifiesStmt(stmt, var);
                pkb.propagateModifiesToParent(stmt, var);
            }
        }
    }

    // DesignExtractor

    private void propagateCallUses() {
        boolean changed;
        do {
            changed = false;
            for (String caller : pkb.getAllProcedures()) {
                for (String callee : pkb.getCallsStar(caller)) {
                    for (String var : pkb.getUsedByProc(callee)) {
                        if (pkb.setUsesProc(caller, var)) {
                            changed = true;
                        }
                    }
                }
            }
        } while (changed);
        for (Integer callStmt : pkb.getAllCallStmts()) {
            String callee = pkb.getCalledProcByStmt(callStmt);
            for (String var : pkb.getUsedByProc(callee)) {
                pkb.setUsesStmt(callStmt, var);
                pkb.propagateUsesToParent(callStmt, var);
            }
        }
    }


    private void addNextEdge(int from, int to) {
        pkb.addNext(from, to);
        cfg.computeIfAbsent(from, k -> new HashSet<>()).add(to);
    }

    private void collectConstants(TNode n) {
        if (n == null) return;

        if (n.getType() == EntityType.CONSTANT) {
            pkb.addConstant(n.getAttr());
        }
        for (TNode c = n.getFirstChild(); c != null; c = c.getRightSibling()) {
            collectConstants(c);
        }
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