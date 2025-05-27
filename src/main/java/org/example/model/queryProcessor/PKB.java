package org.example.model.queryProcessor;

import org.example.model.ast.TNode;
import org.example.model.enums.EntityType;

import java.util.*;
import java.util.stream.Collectors;

public class PKB {
    private final Map<Integer, Integer> parentMap = new HashMap<>();
    private final Map<Integer, Set<Integer>> parentStarCache = new HashMap<>();
    private final Map<Integer, Set<Integer>> childrenMap = new HashMap<>();
    private final Map<Integer, Set<Integer>> descendantCache = new HashMap<>();

    private final Map<Integer, Integer> followsMap = new HashMap<>();
    private final Map<Integer, Set<Integer>> followsStarCache = new HashMap<>();
    private final Map<Integer, Integer> followedByMap = new HashMap<>();
    private final Map<Integer, Set<Integer>> followedByStarCache = new HashMap<>();

    private final Map<Integer, Set<String>> modifiesStmt = new HashMap<>();
    private final Map<String, Set<String>> modifiesProc = new HashMap<>();

    private final Map<Integer, Set<String>> usesStmt = new HashMap<>();
    private final Map<String, Set<String>> usesProc = new HashMap<>();

    private final Map<Integer, EntityType> entitiyTypeMap = new HashMap<>();
    private final Set<String> procedures = new HashSet<>();
    private final Set<String> variables = new HashSet<>();

    private final Map<String, Set<String>> callsMap = new HashMap<>();
    private final Map<String, Set<String>> callsStarCache = new HashMap<>();

    private final Map<Integer, String> callStmtToProc = new HashMap<>();

    private final Map<String, Set<Integer>> varToStmtsUsingIt = new HashMap<>();
    private final Map<String, Set<String>> procToVarsUsedInIt = new HashMap<>();

    private final Map<Integer, Set<Integer>> nextMap = new HashMap<>();
    private final Map<Integer, Set<Integer>> nextStarMap = new HashMap<>();

    private final Map<String, Set<Integer>> assignLhsToStmts = new HashMap<>();

    private final Map<Integer, Set<String>> ifControlVars = new HashMap<>();
    private final Set<String> constants = new HashSet<>();

    public void addConstant(String value){ constants.add(value); }

public Set<String> getAllConstants() { return new HashSet<>(constants); }
    public void setAssignLhs(int stmt, String var) {
        assignLhsToStmts.computeIfAbsent(var, k -> new HashSet<>()).add(stmt);
    }
    public Set<Integer> getAssignsWithLhs(String var) {
        return assignLhsToStmts.getOrDefault(var, Set.of());
    }

    private final Map<Integer, TNode> assignRhsTree = new HashMap<>();
    public void setAssignRhsTree(int stmt, TNode exprRoot) {
        assignRhsTree.put(stmt, exprRoot);
    }
    public TNode getAssignRhsTree(int stmt) {
        return assignRhsTree.get(stmt);
    }

    private final Map<Integer, Set<String>> whileControlVars = new HashMap<>();

    public void setWhileControlVars(int stmt, Set<String> vars) {
        whileControlVars.put(stmt, vars);
    }

    public Set<String> getWhileControlVars(int stmt) {
        return whileControlVars.getOrDefault(stmt, Set.of());
    }
    public Set<Integer> getStmtsUsingVar(String varName) {
        return varToStmtsUsingIt.getOrDefault(varName, new HashSet<>());
    }

    public Set<String> getVarsUsedInProc(String procName) {
        return procToVarsUsedInIt.getOrDefault(procName, new HashSet<>());
    }

    public void setCallStmt(int stmt, String proc) {
        callStmtToProc.put(stmt, proc);
    }

    public String getCalledProcByStmt(int stmt) {
        return callStmtToProc.get(stmt);
    }

    public EntityType getEntityType(String entityIdentifier) {
        try {
            int stmtNum = Integer.parseInt(entityIdentifier);
            EntityType type = entitiyTypeMap.get(stmtNum);
            if (type != null) {
                return type;
            }
        } catch (NumberFormatException ignored) { }

        if (assignLhsToStmts.containsKey(entityIdentifier)) {
            return EntityType.VARIABLE;
        }

        if (modifiesProc.containsKey(entityIdentifier)) {
            return EntityType.PROCEDURE;
        }

        throw new IllegalArgumentException("Unknown entity identifier: " + entityIdentifier);
    }

    public EntityType getEntityType(int stmt) {
        return entitiyTypeMap.get(stmt);
    }

    public void setParent(int parent, int child) {
        parentMap.put(child, parent);
        childrenMap.computeIfAbsent(parent, k -> new HashSet<>()).add(child);
    }

    public int getParent(int child) {
        return parentMap.getOrDefault(child, -1);
    }

    public Map<Integer, Integer> getParentMap() {
        return parentMap;
    }

    public Set<Integer> getParentStar(int child) {
        if (!parentStarCache.containsKey(child)) {
            Set<Integer> ancestors = new HashSet<>();
            int current = child;
            while (parentMap.containsKey(current)) {
                current = parentMap.get(current);
                ancestors.add(current);
            }
            parentStarCache.put(child, ancestors);
        }
        return parentStarCache.get(child);
    }

    public Map<Integer, Set<Integer>> getParentStarMap() {
        return parentStarCache;
    }

    public Set<Integer> getParentedBy(int parent) {
        return childrenMap.getOrDefault(parent, new HashSet<>());
    }

    public Set<Integer> getParentedStarBy(int parent) {
        if (!descendantCache.containsKey(parent)) {
            computeDescendants(parent);
        }
        return new HashSet<>(descendantCache.get(parent));
    }

    public void setIfControlVars(int stmt, Set<String> vars){
        ifControlVars.put(stmt, vars);
    }
    public Set<String> getIfControlVars(int stmt){
        return ifControlVars.getOrDefault(stmt, Set.of());
    }

    private void computeDescendants(int parent) {
        Set<Integer> descendants = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(parent);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int child : childrenMap.getOrDefault(current, new HashSet<>())) {
                if (descendants.add(child)) {
                    queue.add(child);
                }
            }
        }
        descendantCache.put(parent, descendants);
    }

    public void setFollows(int predecessor, int successor) {
        followsMap.put(predecessor, successor);
        followedByMap.put(successor, predecessor);
        followsStarCache.clear();
        followedByStarCache.clear();
    }

    public Map<Integer, Integer> getAllFollows() {
        return followsMap;
    }

    public Integer getFollows(int stmt) {
        return followsMap.get(stmt);
    }

    public Map<Integer, Integer> getAllFollowedBy() {
        return followedByMap;
    }

    public Integer getFollowedBy(int stmt) {
        return followedByMap.get(stmt);
    }

    public Set<Integer> getFollowsStar(int stmt) {
        if (!followsStarCache.containsKey(stmt)) {
            Set<Integer> followers = new HashSet<>();
            Integer current = followsMap.get(stmt);
            while (current != null) {
                followers.add(current);
                current = followsMap.get(current);
            }
            followsStarCache.put(stmt, followers);
        }
        return new HashSet<>(followsStarCache.get(stmt));
    }

    public Set<Integer> getFollowedByStar(int stmt) {
        if (!followedByStarCache.containsKey(stmt)) {
            Set<Integer> predecessors = new HashSet<>();
            Integer current = followedByMap.get(stmt);
            while (current != null) {
                predecessors.add(current);
                current = followedByMap.get(current);
            }
            followedByStarCache.put(stmt, predecessors);
        }
        return new HashSet<>(followedByStarCache.get(stmt));
    }

    public void setModifiesStmt(int stmt, String var) {
        modifiesStmt.computeIfAbsent(stmt, k -> new HashSet<>()).add(var);
    }

    public boolean setModifiesProc(String proc, String var) {
        if (!modifiesProc.containsKey(proc)) {
            modifiesProc.put(proc, new HashSet<>());
        }
        return modifiesProc.get(proc).add(var);
    }

    public Set<String> getModifiedByStmt(int stmt) {
        return modifiesStmt.getOrDefault(stmt, new HashSet<>());
    }

    public Map<Integer, Set<String>> getModifiedByStmtMap() {
        return new HashMap<>(modifiesStmt);
    }

    public Set<String> getModifiedByProc(String proc) {
        return modifiesProc.getOrDefault(proc, new HashSet<>());
    }

    public Map<String, Set<String>> getModifiedByProcMap() {
        return new HashMap<>(modifiesProc);
    }

    public Map<Integer, Set<String>> getAllUses() {
        return new HashMap<>(usesStmt);
    }

    public void setUsesStmt(int stmt, String var) {
        usesStmt.computeIfAbsent(stmt, k -> new HashSet<>()).add(var);
        varToStmtsUsingIt.computeIfAbsent(var, k -> new HashSet<>()).add(stmt);
    }

    public Map<String, Set<String>> getAllUsesProc() {
        return usesProc;
    }

    public void setUsesProc(String proc, String var) {
        usesProc.computeIfAbsent(proc, k -> new HashSet<>()).add(var);
        procToVarsUsedInIt.computeIfAbsent(proc, k -> new HashSet<>()).add(var);
    }

    public Set<String> getUsedByStmt(int stmt) {
        return usesStmt.getOrDefault(stmt, new HashSet<>());
    }

    public Set<String> getUsedByProc(String proc) {
        return usesProc.getOrDefault(proc, new HashSet<>());
    }

    public Set<Integer> getAllCallStmts() { return new HashSet<>(callStmtToProc.keySet()); }

    public void propagateUsesToParent(int stmt, String var) {
        int current = stmt;
        while (parentMap.containsKey(current)) {
            current = parentMap.get(current);
            setUsesStmt(current, var);
        }
    }

    public void propagateModifiesToParent(int stmt, String var) {
        int current = stmt;
        while (parentMap.containsKey(current)) {
            current = parentMap.get(current);
            setModifiesStmt(current, var);
        }
    }

    public void addStmt(int stmtNumber, EntityType type) {
        entitiyTypeMap.put(stmtNumber, type);
    }

    public void addProcedure(String procName) {
        procedures.add(procName);
    }

    public void addVariable(String varName) {
        variables.add(varName);
    }


public void printState() {
    System.out.println("=== PKB State ===");

    // parentMap: child -> parent
    System.out.println("Parent map (child -> parent):");
    for (Map.Entry<Integer, Integer> entry : parentMap.entrySet()) {
        System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
    }
    System.out.println();

    // parentStarCache: child -> set of all ancestors
    System.out.println("Parent* cache (child -> ancestors):");
    for (Map.Entry<Integer, Set<Integer>> entry : parentStarCache.entrySet()) {
        System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
    }
    System.out.println();

    // childrenMap: parent -> set of direct children
    System.out.println("Children map (parent -> children):");
    for (Map.Entry<Integer, Set<Integer>> entry : childrenMap.entrySet()) {
        System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
    }
    System.out.println();

    // followsMap: stmt -> immediate follower
    // System.out.println("Follows map (stmt -> next stmt):");
    // for (Map.Entry<Integer, Integer> entry : followsMap.entrySet()) {
    //     System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
    // }
    // System.out.println();
    //
    // // followedByMap: stmt -> statement that comes before it
    // System.out.println("FollowedBy map (stmt -> previous stmt):");
    // for (Map.Entry<Integer, Integer> entry : followedByMap.entrySet()) {
    //     System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
    // }
    // System.out.println();
    //
    // // modifiesStmt: stmt -> set of variables
    // System.out.println("Modifies (stmt -> variables):");
    // for (Map.Entry<Integer, Set<String>> entry : modifiesStmt.entrySet()) {
    //     System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
    // }
    // System.out.println();
    //
    // // modifiesProc: procName -> set of variables
    // System.out.println("Modifies (procedure -> variables):");
    // for (Map.Entry<String, Set<String>> entry : modifiesProc.entrySet()) {
    //     System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
    // }
    // System.out.println();
    //
    // // usesStmt: stmt -> set of variables
    // System.out.println("Uses (stmt -> variables):");
    // for (Map.Entry<Integer, Set<String>> entry : usesStmt.entrySet()) {
    //     System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
    // }
    // System.out.println();
    //
    // // usesProc: procName -> set of variables
    // System.out.println("Uses (procedure -> variables):");
    // for (Map.Entry<String, Set<String>> entry : usesProc.entrySet()) {
    //     System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
    // }
    // System.out.println();
    //
    // // stmtTypeMap: stmt -> EntityType (ASSIGN, WHILE, IF, etc.)
    // System.out.println("Statement types (stmt -> EntityType):");
    // for (Map.Entry<Integer, EntityType> entry : stmtTypeMap.entrySet()) {
    //     System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
    // }
    // System.out.println();
    //
    // // procedures: set of procedure names
    // System.out.println("Procedures (set of all procedure names):");
    // for (String procName : procedures) {
    //     System.out.println("  " + procName);
    // }
    // System.out.println();

    // Możesz dodać analogiczne sekcje dla followsStarCache, followedByStarCache, itp.
    // w zależności od tego, co przechowujesz w PKB.
    // Przykładowo:
    // System.out.println("Follows* cache (stmt -> set of all next stmts):");
    // for (Map.Entry<Integer, Set<Integer>> entry : followsStarCache.entrySet()) {
    //     System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
    // }
}

    public void setCalls(String caller, String callee) {
        callsMap.computeIfAbsent(caller, k -> new HashSet<>()).add(callee);
    }

    public Set<String> getCallsStar(String caller) {
        if (!callsStarCache.containsKey(caller)) {
            Set<String> calledProcedures = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(caller);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                for (String callee : callsMap.getOrDefault(current, new HashSet<>())) {
                    if (calledProcedures.add(callee)) {
                        queue.add(callee);
                    }
                }
            }
            callsStarCache.put(caller, calledProcedures);
        }
        return new HashSet<>(callsStarCache.get(caller));
    }

    public Set<String> getCalls(String caller) {
        return callsMap.getOrDefault(caller, new HashSet<>());
    }

    public Map<String, Set<String>> getCallsMap() {
        return new HashMap<>(callsMap);
    }

    public Set<Integer> getAllStmts() {
        return entitiyTypeMap.keySet();
    }

    public Set<String> getStmtsByType(EntityType type) {
        switch (type) {
            case VARIABLE:
                return assignLhsToStmts.keySet();
            case PROCEDURE:
                return procToVarsUsedInIt.keySet();
            default:
                return entitiyTypeMap.entrySet().stream()
                        .filter(e -> e.getValue() == type)
                        .map(Map.Entry::getKey)
                        .map(String::valueOf)
                        .collect(Collectors.toSet());
        }
    }

    public Set<String> getAllProcedures() {
        return new HashSet<>(procedures);
    }
    public Set<String> getAllVariables() {
        return new HashSet<>(variables);
    }

    public Map<Integer, Set<Integer>> getAllNext() {
        return new HashMap<>(nextMap);
    }

    public void addNext(int from, int to) {
        nextMap.computeIfAbsent(from, k -> new HashSet<>()).add(to);
    }

    public Set<Integer> getNext(int stmt) {
        return nextMap.getOrDefault(stmt, Set.of());
    }

    public void addNextStar(int from, int to) {
        nextStarMap.computeIfAbsent(from, k -> new HashSet<>()).add(to);
    }

    public Set<Integer> getNextStar(int stmt) {
        return nextStarMap.getOrDefault(stmt, Set.of());
    }

    public boolean treesEqual(TNode a, TNode b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (!a.getType().equals(b.getType())) return false;
        if (!Objects.equals(a.getAttr(), b.getAttr())) return false;

        TNode ca = a.getFirstChild();
        TNode cb = b.getFirstChild();
        while (ca != null && cb != null) {
            if (!treesEqual(ca, cb)) return false;
            ca = ca.getRightSibling();
            cb = cb.getRightSibling();
        }
        return ca == null && cb == null;
    }

    public boolean containsTopLevelSubtree(TNode root, TNode pattern) {
        if (root == null || pattern == null) return false;

        Deque<TNode> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            TNode current = stack.pop();

            if (treesEqual(current, pattern)) return true;

            for (TNode child = current.getFirstChild(); child != null; child = child.getRightSibling()) {
                stack.push(child);
            }
        }
        return false;
    }
}
