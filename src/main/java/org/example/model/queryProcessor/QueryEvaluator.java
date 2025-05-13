package org.example.model.queryProcessor;

import org.example.model.enums.EntityType;
import org.example.model.queryProcessor.Synonym;
import org.example.model.queryProcessor.SynonymType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QueryEvaluator {
    private final PKB pkb;
    private final Validator validator;
    private Set<Synonym> synonyms;

    public QueryEvaluator(PKB pkb) {
        this.pkb = pkb;
        this.validator = new Validator();
    }

    public Set<String> evaluateQuery(String query) {
        try {
            if (!validator.isValid(query)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            System.out.println("#Query is not valid");
            return Collections.emptySet();
        }
        synonyms = validator.getSynonyms();
        String[] split = query.split(";");
        String queryToProcess = toUpperCaseOutsideQuotes(split[split.length - 1].trim());
        Set<String> result = processQuery(queryToProcess);
        if (result.isEmpty()) result.add("none");
        return result;
    }

    private Set<String> processQuery(String query) {
        List<Relationship> relationships = extractRelationships(query);
        Map<String, Set<String>> partialSolutions = initSynonymMap();
        boolean first = true;

        for (Relationship r : relationships) {
            Map<String, Set<String>> localResults = initSynonymMap();
            applyRelationship(r, localResults);

            for (String syn : localResults.keySet()) {
                Set<String> vals = localResults.get(syn);
                if (first) {
                    partialSolutions.put(syn, new HashSet<>(vals));
                } else {
                    partialSolutions.get(syn).retainAll(vals);
                }
            }
            first = false;
        }

        return finalizeResult(query, partialSolutions);
    }

    private void applyRelationship(Relationship r, Map<String, Set<String>> partial) {
        String left = r.getFirstArg();
        String right = r.getSecondArg();
        switch (r.getType()) {
            case CALLS_STAR -> handleCallsStar(left, right, partial);
            case PARENT_STAR -> handleParentStar(left, right, partial);
            case FOLLOWS_STAR -> handleFollowsStar(left, right, partial);
            case CALLS -> handleCalls(left, right, partial);
            case PARENT -> handleParent(left, right, partial);
            case FOLLOWS -> handleFollows(left, right, partial);
            case MODIFIES -> handleModifies(left, right, partial);
            case USES -> handleUses(left, right, partial);
        }
    }

    private List<Relationship> extractRelationships(String query) {
        List<Relationship> result = new ArrayList<>();

        Pattern pattern = Pattern.compile("\\b(FOLLOWS\\*?|PARENT\\*?|CALLS\\*?|MODIFIES|USES)\\s*\\(([^,\\)]+)\\s*,\\s*([^\\)]+)\\)");
        Matcher matcher = pattern.matcher(query);

        while (matcher.find()) {
            String relName = matcher.group(1).toUpperCase();
            String arg1 = matcher.group(2).trim();
            String arg2 = matcher.group(3).trim();

            RelationshipType type = parseRelationshipType(relName);
            if (type != null) {
                result.add(new Relationship(type, arg1 + ", " + arg2));
            }
        }

        return result;
    }

    private RelationshipType parseRelationshipType(String name) {
        switch (name.toUpperCase()) {
            case "CALLS" -> {
                return RelationshipType.CALLS;
            }
            case "CALLS*" -> {
                return RelationshipType.CALLS_STAR;
            }
            case "PARENT" -> {
                return RelationshipType.PARENT;
            }
            case "PARENT*" -> {
                return RelationshipType.PARENT_STAR;
            }
            case "FOLLOWS" -> {
                return RelationshipType.FOLLOWS;
            }
            case "FOLLOWS*" -> {
                return RelationshipType.FOLLOWS_STAR;
            }
            case "MODIFIES" -> {
                return RelationshipType.MODIFIES;
            }
            case "USES" -> {
                return RelationshipType.USES;
            }
            default -> {
                return null;
            }
        }
    }

    private Map<String, Set<String>> initSynonymMap() {
        Map<String, Set<String>> map = new HashMap<>();
        for (Synonym syn : synonyms) {
            map.put(syn.name(), new HashSet<>());
        }
        return map;
    }

    private void handleCallsStar(String left, String right, Map<String, Set<String>> partialSolutions) {
        String caller = left.replace("\"", "");
        String callee = right.replace("\"", "");

        if (synonymsContain(left) && synonymsContain(right)) {
            for (String proc : pkb.getCallsMap().keySet()) {
                Set<String> allCallees = pkb.getCallsStar(proc);
                for (String c : allCallees) {
                    partialSolutions.get(left).add(proc);
                    partialSolutions.get(right).add(c);
                }
            }
        } else if (isStringLiteral(left) && synonymsContain(right)) {
            Set<String> callees = pkb.getCallsStar(caller);
            partialSolutions.get(right).addAll(callees);
        } else if (isStringLiteral(right) && synonymsContain(left)) {
            for (String proc : pkb.getCallsMap().keySet()) {
                if (pkb.getCallsStar(proc).contains(callee)) {
                    partialSolutions.get(left).add(proc);
                }
            }
        }
    }

    private void handleParentStar(String left, String right, Map<String, Set<String>> partialSolutions) {

        if (isNumeric(right) && synonymsContain(left)) {
            int c = Integer.parseInt(right);
            Set<Integer> parents = pkb.getParentStar(c);
            if (parents.isEmpty()) {
                partialSolutions.get(left).add("none");
            } else {
                if (partialSolutions.containsKey("W") && partialSolutions.size()>1) {
                    Set<String> rightResults = new HashSet<>();
                    for (int w : pkb.getAllStmts()) {
                        rightResults.add(String.valueOf(w));
                    }
                    partialSolutions.put("W", rightResults);
                } else {
                    for (int p : parents)
                        partialSolutions.get(left).add(String.valueOf(p));
                }
            }
        }
        if (isNumeric(left) && synonymsContain(right)) {
            int p = Integer.parseInt(left);
            Set<Integer> descendants = pkb.getParentedStarBy(p);
            if (descendants.isEmpty()) {
                partialSolutions.get(right).add("none");
            } else {
                for (int d : descendants)
                    partialSolutions.get(right).add(String.valueOf(d));
            }
        }
    }

    private void handleFollowsStar(String left, String right, Map<String, Set<String>> partialSolutions) {
        if (isNumeric(right) && synonymsContain(left)) {
            int c = Integer.parseInt(right);
            Set<Integer> parents = pkb.getFollowedByStar(c);
            for (int p : parents)
                partialSolutions.get(left).add(String.valueOf(p));
        }
        if (isNumeric(left) && synonymsContain(right)) {
            int p = Integer.parseInt(left);
            Set<Integer> descendants = pkb.getFollowsStar(p);
            for (int d : descendants)
                partialSolutions.get(right).add(String.valueOf(d));
        }
        if(synonymsContain(left) && synonymsContain(right)){
            Set<String> leftResults = new HashSet<>();
            Set<String> rightResults = new HashSet<>();
            Set<Integer> parents = pkb.getAllStmts();
            for (int s : pkb.getAllStmts()) {
                Set<Integer> followers = pkb.getFollowsStar(s);
                for (int w : followers) {
                    leftResults.add(String.valueOf(s));
                    rightResults.add(String.valueOf(w));
                }
            }

            partialSolutions.put(left, leftResults);
            partialSolutions.put(right, rightResults);
        }
    }

    private void handleCalls(String left, String right, Map<String, Set<String>> partialSolutions) {
        String caller = left.replace("\"", "");
        String callee = right.replace("\"", "");

        if (synonymsContain(left) && synonymsContain(right)) {
            for (Map.Entry<String, Set<String>> entry : pkb.getCallsMap().entrySet()) {
                String procCaller = entry.getKey();
                for (String procCallee : entry.getValue()) {
                    partialSolutions.get(left).add(procCaller);
                    partialSolutions.get(right).add(procCallee);
                }
            }
        } else if (isStringLiteral(left) && synonymsContain(right)) {
            Set<String> callees = pkb.getCalls(caller);
            partialSolutions.get(right).addAll(callees);
        } else if (isStringLiteral(right) && synonymsContain(left)) {
            for (Map.Entry<String, Set<String>> entry : pkb.getCallsMap().entrySet()) {
                if (entry.getValue().contains(callee)) {
                    partialSolutions.get(left).add(entry.getKey());
                }
            }
        }
    }

    private void handleParent(String left, String right, Map<String, Set<String>> partialSolutions) {
        if (isNumeric(right) && synonymsContain(left)) {
            int c = Integer.parseInt(right);
            Integer i = pkb.getParent(c);
            if (i == -1) {
                partialSolutions.get(left).add("none");
                return;
            }
            int p = i;
            if (p > 0)
                partialSolutions.get(left).add(String.valueOf(p));
        }
        if (isNumeric(left) && synonymsContain(right)) {
            int p = Integer.parseInt(left);
            Set<Integer> kids = pkb.getParentedBy(p);
            if (kids.isEmpty()) {
                partialSolutions.get(right).add("none");
            } else {
                for (int k : kids)
                    partialSolutions.get(right).add(String.valueOf(k));
            }
        }
    }

    private void handleFollows(String left, String right, Map<String, Set<String>> partialSolutions) {
        if (isNumeric(right) && synonymsContain(left)) {
            int r = Integer.parseInt(right);
            Integer i = pkb.getFollowedBy(r);
            if (i == null) {
                partialSolutions.get(left).add("none");
                return;
            }
            int f = i;
            if (f > 0)
                partialSolutions.get(left).add(String.valueOf(f));
        }
        if (isNumeric(left) && synonymsContain(right)) {
            int f = Integer.parseInt(left);
            Integer succ = pkb.getFollows(f);
            if (succ != null)
                partialSolutions.get(right).add(String.valueOf(succ));
        }
    }

    private void handleModifies(String left,
                                String right,
                                Map<String, Set<String>> partial) {

        boolean leftIsNum = isNumeric(left);
        boolean rightIsNum = isNumeric(right);

        boolean leftIsLit = isStringLiteral(left);
        boolean rightIsLit = isStringLiteral(right);

        boolean rightIsBareLit = !rightIsNum && !synonymsContain(right) && !rightIsLit;
        boolean leftIsBareLit = !leftIsNum && !synonymsContain(left) && !leftIsLit;

        String rightLit = rightIsLit ? right.replace("\"", "")
                : rightIsBareLit ? right
                : null;

        String leftLit = leftIsLit ? left.replace("\"", "")
                : leftIsBareLit ? left
                : null;

        if (synonymsContain(left) && rightLit != null) {
            for (int stmt : pkb.getAllStmts()) {
                if (pkb.getModifiedByStmt(stmt).stream()
                        .anyMatch(v -> v.equalsIgnoreCase(rightLit))) {
                    partial.get(left).add(String.valueOf(stmt));
                }
            }
            for (String proc : pkb.getAllProcedures()) {
                if (pkb.getModifiedByProc(proc).stream()
                        .anyMatch(v -> v.equalsIgnoreCase(rightLit))) {
                    partial.get(left).add(proc);
                }
            }
            return;
        }

        if (leftIsNum && synonymsContain(right)) {
            partial.get(right).addAll(pkb.getModifiedByStmt(Integer.parseInt(left)));
        }

        if (leftLit != null && synonymsContain(right)) {
            partial.get(right).addAll(pkb.getModifiedByProc(leftLit));
        }

        if (!leftIsNum && synonymsContain(left) && synonymsContain(right)) {
            for (String proc : pkb.getAllProcedures()) {
                for (String var : pkb.getModifiedByProc(proc)) {
                    partial.get(left).add(proc);
                    partial.get(right).add(var);
                }
            }
        }

        if (synonymsContain(left) && synonymsContain(right)) {
            for (int stmt : pkb.getAllStmts()) {
                for (String var : pkb.getModifiedByStmt(stmt)) {
                    partial.get(left).add(String.valueOf(stmt));
                    partial.get(right).add(var);
                }
            }
        }

        if (synonymsContain(left) && rightLit != null) {
            for (String proc : pkb.getAllProcedures()) {
                if (pkb.getModifiedByProc(proc).stream()
                        .anyMatch(v -> v.equalsIgnoreCase(rightLit))) {
                    partial.get(left).add(proc);
                }
            }
        }
    }

    private void handleUses(String left, String right,
                            Map<String, Set<String>> partial) {

        boolean leftIsNum = isNumeric(left);
        boolean rightIsNum = isNumeric(right);

        boolean leftIsLit = isStringLiteral(left);
        boolean rightIsLit = isStringLiteral(right);

        String leftLit = leftIsLit ? left.substring(1, left.length() - 1) : null;
        String rightLit = rightIsLit ? right.substring(1, right.length() - 1) : null;

        if (isStmtSyn(left) && rightLit != null) {
            for (int stmt : pkb.getAllStmts()) {
                if (pkb.getUsedByStmt(stmt).stream()
                        .anyMatch(v -> v.equalsIgnoreCase(rightLit))) {
                    partial.get(left).add(String.valueOf(stmt));
                }
            }
            return;
        }

        if (leftIsNum && synonymsContain(right)) {
            partial.get(right).addAll(pkb.getUsedByStmt(Integer.parseInt(left)));
            return;
        }

        if (leftIsLit && synonymsContain(right)) {
            partial.get(right).addAll(pkb.getUsedByProc(leftLit));
            return;
        }

        if (isProcSyn(left) && synonymsContain(right)) {
            for (String proc : pkb.getAllProcedures()) {
                for (String var : pkb.getUsedByProc(proc)) {
                    partial.get(left).add(proc);
                    partial.get(right).add(var);
                }
            }
            return;
        }

        if (isStmtSyn(left) && synonymsContain(right)) {
            for (int stmt : pkb.getAllStmts()) {
                for (String var : pkb.getUsedByStmt(stmt)) {
                    partial.get(left).add(String.valueOf(stmt));
                    partial.get(right).add(var);
                }
            }
            return;
        }

        if (isProcSyn(left) && rightLit != null) {
            for (String proc : pkb.getAllProcedures()) {
                if (pkb.getUsedByProc(proc).contains(rightLit)) {
                    partial.get(left).add(proc);
                }
            }
        }
    }

    private boolean isStringLiteral(String s) {
        return s.startsWith("\"") && s.endsWith("\"");
    }

    private Set<String> finalizeResult(String query, Map<String, Set<String>> partialSolutions) {
        partialSolutions = handleWith(partialSolutions, query);

        if (partialSolutions.values().stream().anyMatch(s -> s.stream().anyMatch(v -> "none".equalsIgnoreCase(v)))) {
            return Set.of("none");
        }

        String sel = query.split("SELECT")[1].trim()
                .split("SUCH THAT|WITH")[0].trim()
                .split("\\s+")[0].toUpperCase();

        Set<String> result = new LinkedHashSet<>(partialSolutions.getOrDefault(sel, Set.of()));
        if (result.isEmpty()) return Set.of("none");

        Synonym targetSyn = synonyms.stream()
                .filter(s -> s.name().equalsIgnoreCase(sel))
                .findFirst()
                .orElse(null);

        if (targetSyn != null) {
            EntityType filter = switch (targetSyn.type()) {
                case ASSIGN -> EntityType.ASSIGN;
                case WHILE  -> EntityType.WHILE;
                case IF     -> EntityType.IF;
                case CALL   -> EntityType.CALL;
                default     -> null;
            };
            if (filter != null) {
                result = result.stream()
                        .filter(v -> {
                            try {
                                return pkb.getStmtType(Integer.parseInt(v)) == filter;
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
        }

        if (result.isEmpty()) return Set.of("none");

        List<String> sorted = new ArrayList<>(result);
        sorted.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
            catch (NumberFormatException e) { return a.compareTo(b); }
        });
        return new LinkedHashSet<>(sorted);
    }

    private boolean synonymsContain(String s) {
        for (Synonym syn : synonyms) {
            if (syn.name().equalsIgnoreCase(s))
                return true;
        }
        return false;
    }

    private boolean isNumeric(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    private Map<String, Set<String>> handleWith(Map<String, Set<String>> partialSolutions, String query) {
        List<WithClause> withClauses = parseWithClauses(query);

        for (WithClause w : withClauses) {
            String[] leftParts = w.left().split("\\.");
            if (leftParts.length != 2) continue;

            String synonym = leftParts[0];
            String attr = leftParts[1].toUpperCase();

            switch (attr) {
                case "STMT#" -> handleStmtWith(partialSolutions, w, synonym);
            }

            updateParentRelations(partialSolutions, synonym);
        }

        return partialSolutions;
    }

    private void updateParentRelations(Map<String, Set<String>> partialSolutions, String synonym) {
        for (String otherSyn : partialSolutions.keySet()) {
            if (otherSyn.equals(synonym)) continue;

            Set<String> candidateValues = new HashSet<>();
            for (String val : partialSolutions.get(synonym)) {
                try {
                    int stmt = Integer.parseInt(val);
                    int parent = pkb.getParent(stmt);
                    if (parent != -1) {
                        candidateValues.add(String.valueOf(parent));
                    }
                } catch (Exception ignored) {}
            }

            if (!candidateValues.isEmpty()) {
                Set<String> current = partialSolutions.getOrDefault(otherSyn, new HashSet<>());
                if (current.isEmpty()) {
                    partialSolutions.put(otherSyn, candidateValues);
                } else {
                    current.retainAll(candidateValues);
                    partialSolutions.put(otherSyn, current);
                }
            }
        }
    }

    private void handleStmtWith(Map<String, Set<String>> partialSolutions, WithClause w, String synonym) {
        String expected = w.right().replace(";", "").trim();

        if (!partialSolutions.containsKey(synonym) || partialSolutions.get(synonym).isEmpty()) {
            partialSolutions.put(synonym, Set.of(expected));
        } else {
            Set<String> existing = partialSolutions.get(synonym);
            Set<String> filtered = existing.stream()
                    .filter(val -> val.equals(expected))
                    .collect(Collectors.toSet());
            partialSolutions.put(synonym, filtered);
        }
    }

    private List<WithClause> parseWithClauses(String query) {
        String[] split = query.split("WITH");
        List<WithClause> withClauses = new ArrayList<>();
        for (int i = 1; i < split.length; i++) {
            String clause = split[i].split("SUCH THAT|AND|SELECT")[0].trim();
            String[] parts = clause.split("=");
            if (parts.length == 2) {
                withClauses.add(new WithClause(parts[0].trim(), parts[1].trim()));
            }
        }
        return withClauses;
    }

    private static String toUpperCaseOutsideQuotes(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        boolean insideQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (ch == '"' && (i == 0 || input.charAt(i - 1) != '\\')) {
                insideQuotes = !insideQuotes;
                sb.append(ch);
            } else {
                sb.append(insideQuotes ? ch : Character.toUpperCase(ch));
            }
        }
        return sb.toString();
    }

    private SynonymType getSynType(String name) {
        return synonyms.stream()
                .filter(s -> s.name().equalsIgnoreCase(name))
                .map(Synonym::type)
                .findFirst()
                .orElse(null);
    }

    private boolean isStmtSyn(String name) {
        return EnumSet.of(SynonymType.STMT,
                        SynonymType.ASSIGN,
                        SynonymType.WHILE,
                        SynonymType.IF,
                        SynonymType.CALL)
                .contains(getSynType(name));
    }

    private boolean isProcSyn(String name) {
        return getSynType(name) == SynonymType.PROCEDURE;
    }
}
