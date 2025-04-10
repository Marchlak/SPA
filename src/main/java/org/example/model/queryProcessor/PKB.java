package org.example.model.queryProcessor;

import org.example.model.enums.EntityType;

import java.util.*;

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

    private final Map<Integer, EntityType> stmtTypeMap = new HashMap<>();
    private final Set<String> procedures = new HashSet<>();

    public void setParent(int parent, int child) {
        parentMap.put(child, parent);
        childrenMap.computeIfAbsent(parent, k -> new HashSet<>()).add(child);
    }

    public int getParent(int child) {
        return parentMap.getOrDefault(child, -1);
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

    public Set<Integer> getParentedBy(int parent) {
        return childrenMap.getOrDefault(parent, new HashSet<>());
    }

    public Set<Integer> getParentedStarBy(int parent) {
        if (!descendantCache.containsKey(parent)) {
            computeDescendants(parent);
        }
        return new HashSet<>(descendantCache.get(parent));
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

    public Integer getFollows(int stmt) {
        return followsMap.get(stmt);
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

    public void setModifiesProc(String proc, String var) {
        modifiesProc.computeIfAbsent(proc, k -> new HashSet<>()).add(var);
    }

    public Set<String> getModifiedByStmt(int stmt) {
        return modifiesStmt.getOrDefault(stmt, new HashSet<>());
    }

    public Set<String> getModifiedByProc(String proc) {
        return modifiesProc.getOrDefault(proc, new HashSet<>());
    }

    public void setUsesStmt(int stmt, String var) {
        usesStmt.computeIfAbsent(stmt, k -> new HashSet<>()).add(var);
    }

    public void setUsesProc(String proc, String var) {
        usesProc.computeIfAbsent(proc, k -> new HashSet<>()).add(var);
    }

    public Set<String> getUsedByStmt(int stmt) {
        return usesStmt.getOrDefault(stmt, new HashSet<>());
    }

    public Set<String> getUsedByProc(String proc) {
        return usesProc.getOrDefault(proc, new HashSet<>());
    }

    public void propagateUsesToParent(int stmt, String var) {
        int current = stmt;
        while (parentMap.containsKey(current)) {
            current = parentMap.get(current);
            setUsesStmt(current, var);
        }
    }

    public void addStmt(int stmtNumber, EntityType type) {
        stmtTypeMap.put(stmtNumber, type);
    }

    public void addProcedure(String procName) {
        procedures.add(procName);
    }
}
