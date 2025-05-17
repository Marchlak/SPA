package org.example.model.queryProcessor;

import org.example.model.ast.ExpressionParser;
import org.example.model.ast.TNode;
import org.example.model.enums.EntityType;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

        boolean isBooleanQuery = queryToProcess.trim().toUpperCase().startsWith("SELECT BOOLEAN");

        Set<String> result = processQuery(queryToProcess);
        if (isBooleanQuery) {
            return Set.of(result.isEmpty() || result.contains("none") ? "false" : "true");
        }

        if (result.isEmpty()) result.add("none");
        return result;
    }

    private Set<String> processQuery(String query) {
        List<Relationship> relationships = extractRelationships(query);
        Map<String, Set<String>> partialSolutions = initSynonymMap();
        for (Relationship r : relationships) {
            RelationshipType t = r.getType();
            String left = r.getFirstArg();
            String right = r.getSecondArg();

            switch (t) {
                case MODIFIES -> handleModifies(left, right, partialSolutions);
                case CALLS -> handleCalls(left, right, partialSolutions);
                case CALLS_STAR -> handleCallsStar(left, right, partialSolutions);
                case PARENT -> handleParent(left, right, partialSolutions);
                case PARENT_STAR -> handleParentStar(left, right, partialSolutions);
                case FOLLOWS -> handleFollows(left, right, partialSolutions);
                case FOLLOWS_STAR -> handleFollowsStar(left, right, partialSolutions);
                case USES -> handleUses(left, right, partialSolutions);
                case NEXT -> handleNext(left, right, partialSolutions);
                case NEXT_STAR -> handleNextStar(left, right, partialSolutions);
            }
        }
        handlePatterns(query, partialSolutions);
        return finalizeResult(query, partialSolutions);
    }

    private List<Relationship> extractRelationships(String query) {
        List<Relationship> result = new ArrayList<>();
        if (query.contains("CALLS*")) {
            result.addAll(extractRelationship(query, RelationshipType.CALLS_STAR));
        }
        if (query.contains("CALLS")) {
            result.addAll(extractRelationship(query, RelationshipType.CALLS));
        }

        if (query.contains("PARENT*")) {
            result.addAll(extractRelationship(query, RelationshipType.PARENT_STAR));
        } else if (query.contains("PARENT")) {
            result.addAll(extractRelationship(query, RelationshipType.PARENT));
        }

        if (query.contains("FOLLOWS*")) {
            result.addAll(extractRelationship(query, RelationshipType.FOLLOWS_STAR));
        } else if (query.contains("FOLLOWS")) {
            result.addAll(extractRelationship(query, RelationshipType.FOLLOWS));
        }

        if (query.contains("MODIFIES")) {
            result.addAll(extractRelationship(query, RelationshipType.MODIFIES));
        }

        if (query.contains("USES")) {
            result.addAll(extractRelationship(query, RelationshipType.USES));
        }

        if (query.contains("NEXT")) {
            result.addAll(extractRelationship(query, RelationshipType.NEXT));
        }
        if (query.contains("NEXT*")) {
            result.addAll(extractRelationship(query, RelationshipType.NEXT_STAR));
        }

        return result;
    }

    private List<PatternClause> extractPatternClauses(String query) {
        List<PatternClause> res = new ArrayList<>();
        String upper = query.toUpperCase();
        int idx = upper.indexOf("PATTERN");
        while (idx != -1) {
            int start = idx + "PATTERN".length();
            int open  = upper.indexOf('(', start);
            int close = upper.indexOf(')', open);
            String syn = query.substring(start, open).trim();
            String inside = query.substring(open + 1, close).trim();
            String[] parts = inside.split("\\s*,\\s*");
            String arg1 = parts.length > 0 ? parts[0] : "_";
            String arg2 = parts.length > 1 ? parts[1] : "_";
            res.add(new PatternClause(syn, arg1, arg2));
            idx = upper.indexOf("PATTERN", close);
        }
        return res;
    }


    private List<Relationship> extractRelationship(String query, RelationshipType type) {
        List<Relationship> relationships = new ArrayList<>();
        String regex = type.getType().endsWith("\\*")
                ? type.getType()
                : "\\b" + type.getType() + "\\b";

        String[] split = query.split(regex);

        for (int i = 1; i < split.length; i++) {
            relationships.add(new Relationship(type, extractRelationshipArgs(split[i])));
        }
        return relationships;
    }

    private String extractRelationshipArgs(String s) {
        String tmp = s.split("\\)")[0].trim();
        String args = tmp.substring(1);
        return args;          // ← zostaw tak, bez .replace("\"","")
    }


    private Map<String, Set<String>> initSynonymMap() {
        Map<String, Set<String>> map = new HashMap<>();
        for (Synonym syn : synonyms) {
            map.put(syn.name(), new HashSet<>());
        }
        return map;
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

    private void handleParentStar(String left, String right, Map<String, Set<String>> partialSolutions) {

        if (isNumeric(right) && synonymsContain(left)) {
            int c = Integer.parseInt(right);
            Set<Integer> parents = pkb.getParentStar(c);
            if (parents.isEmpty()) {
                partialSolutions.get(left).add("none");
            } else {
                for (int p : parents)
                    partialSolutions.get(left).add(String.valueOf(p));
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

    private boolean isStringLiteral(String s) {
        return s.startsWith("\"") && s.endsWith("\"");
    }

    //  private void handleFollowsStar(String left, String right, Map<String, Set<String>> partialSolutions) {
//    if (isNumeric(right) && synonymsContain(left)) {
//      int r = Integer.parseInt(right);
//      Set<Integer> preds = pkb.getFollowedByStar(r);
//      for (int x : preds)
//        partialSolutions.get(left).add(String.valueOf(x));
//    }
//    if (isNumeric(left) && synonymsContain(right)) {
//      int f = Integer.parseInt(left);
//      Set<Integer> succs = pkb.getFollowsStar(f);
//      for (int x : succs)
//        partialSolutions.get(right).add(String.valueOf(x));
//    }
//  }
//
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

//
//  private void handleModifies(String left, String right, Map<String, Set<String>> partialSolutions) {
//    if (isNumeric(left) && synonymsContain(right)) {
//      int stmt = Integer.parseInt(left);
//      Set<String> modifiedVars = pkb.getModifiedByStmt(stmt);
//      partialSolutions.get(right).addAll(modifiedVars);
//    }
//    if (!isNumeric(left) && synonymsContain(left) && right.startsWith("\"") && right.endsWith("\"")) {
//    }
//  }

    private Set<String> finalizeResult(String query, Map<String, Set<String>> partialSolutions) {
        String selectSyn = query.split("SELECT")[1]
                .trim()
                .split("SUCH THAT|WITH")[0]
                .trim();
        if (selectSyn.contains(" ")) {
            selectSyn = selectSyn.split("\\s+")[0];
        }

        partialSolutions = handleWith(partialSolutions, query);

        Set<String> initial = partialSolutions.getOrDefault(selectSyn, Collections.emptySet());

        Set<String> filtered = initial.stream()
                .filter(s -> !s.isBlank() && !"none".equalsIgnoreCase(s))
                .collect(Collectors.toSet());

        final String selName = selectSyn;   // effectively-final
        Synonym sel = synonyms.stream()
                .filter(syn -> syn.name().equalsIgnoreCase(selName))
                .findFirst()
                .orElse(null);

        if (sel != null) {
            SynonymType st = sel.type();
            EntityType wanted = null;
            switch (st) {
                case ASSIGN -> wanted = EntityType.ASSIGN;
                case WHILE  -> wanted = EntityType.WHILE;
                case IF     -> wanted = EntityType.IF;
                case CALL   -> wanted = EntityType.CALL;
                default     -> wanted = null;
            }
            if (wanted != null) {
                EntityType filterType = wanted;
                filtered = filtered.stream()
                        .filter(s -> {
                            try {
                                int num = Integer.parseInt(s);
                                return pkb.getStmtType(num) == filterType;
                            } catch (Exception ex) {
                                return false;
                            }
                        })
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
        }

        List<String> sorted = new ArrayList<>(filtered);
        sorted.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });

        if (sorted.isEmpty()) {
            return Set.of("none");
        }

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
                case "VALUE" -> handleValueWith(partialSolutions, w, synonym);
            }

            updateParentRelations(partialSolutions, synonym);
        }

        return partialSolutions;
    }

    private void handleValueWith(Map<String, Set<String>> partial, WithClause w, String constantSyn) {
        String expected = w.right().replace(";", "").trim();

        if (!partial.containsKey(constantSyn) || partial.get(constantSyn).isEmpty()) {
            partial.put(constantSyn, Set.of(expected));
            return;
        }

        Set<String> filtered = partial.get(constantSyn).stream()
                .filter(v -> v.equals(expected))
                .collect(Collectors.toSet());
        partial.put(constantSyn, filtered);
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

    private void handleNext(String left, String right, Map<String, Set<String>> partial) {
        if (isNumeric(left) && isNumeric(right)) {
            int l = Integer.parseInt(left), r = Integer.parseInt(right);
            if (pkb.getNext(l).contains(r)) {
                partial.computeIfAbsent("BOOLEAN", k -> new HashSet<>()).add("T");
            }
            return;
        }

        if (isNumeric(left) && synonymsContain(right)) {
            int l = Integer.parseInt(left);
            partial.computeIfAbsent(right, k -> new HashSet<>())
                    .addAll(pkb.getNext(l).stream().map(String::valueOf).toList());
            return;
        }

        if (synonymsContain(left) && isNumeric(right)) {
            int r = Integer.parseInt(right);
            Set<String> set = partial.computeIfAbsent(left, k -> new HashSet<>());
            for (int stmt : pkb.getAllStmts()) {
                if (pkb.getNext(stmt).contains(r)) set.add(String.valueOf(stmt));
            }
            return;
        }

        if (synonymsContain(left) && synonymsContain(right)) {
            Set<String> lSet = partial.computeIfAbsent(left,  k -> new HashSet<>());
            Set<String> rSet = partial.computeIfAbsent(right, k -> new HashSet<>());
            for (int from : pkb.getAllStmts()) {
                for (int to : pkb.getNext(from)) {
                    lSet.add(String.valueOf(from));
                    rSet.add(String.valueOf(to));
                }
            }
        }
    }

    private void handleNextStar(String left, String right, Map<String, Set<String>> partial) {
        if (isNumeric(left) && isNumeric(right)) {
            int l = Integer.parseInt(left), r = Integer.parseInt(right);
            if (pkb.getNextStar(l).contains(r)) {
                partial.computeIfAbsent("BOOLEAN", k -> new HashSet<>()).add("T");
            }
            return;
        }

        if (isNumeric(left) && synonymsContain(right)) {
            int l = Integer.parseInt(left);
            partial.computeIfAbsent(right, k -> new HashSet<>())
                    .addAll(pkb.getNextStar(l).stream().map(String::valueOf).toList());
            return;
        }

        if (synonymsContain(left) && isNumeric(right)) {
            int r = Integer.parseInt(right);
            Set<String> set = partial.computeIfAbsent(left, k -> new HashSet<>());
            for (int stmt : pkb.getAllStmts()) {
                if (pkb.getNextStar(stmt).contains(r)) set.add(String.valueOf(stmt));
            }
            return;
        }

        if (synonymsContain(left) && synonymsContain(right)) {
            Set<String> lSet = partial.computeIfAbsent(left,  k -> new HashSet<>());
            Set<String> rSet = partial.computeIfAbsent(right, k -> new HashSet<>());
            for (int from : pkb.getAllStmts()) {
                for (int to : pkb.getNextStar(from)) {
                    lSet.add(String.valueOf(from));
                    rSet.add(String.valueOf(to));
                }
            }
        }
    }

    private void handlePatterns(String query, Map<String, Set<String>> partial) {
        Pattern p = Pattern.compile(
                "pattern\\s+([A-Za-z][A-Za-z0-9]*)\\s*\\(([^()]*?(?:\\([^()]*\\)[^()]*)*?)\\)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher m = p.matcher(query);
        while (m.find()) {
            String synonymName = m.group(1).trim();
            String argsRaw = m.group(2).trim();

            SynonymType synType = synonyms.stream()
                    .filter(s -> s.name().equalsIgnoreCase(synonymName))
                    .map(Synonym::type)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown synonym in PATTERN clause: " + synonymName));

            List<String> args = splitPatternArgs(argsRaw);

            if (args.size() == 2) {
                handlePattern2Args(synType, synonymName, args.get(0), args.get(1), partial);
            } else if (args.size() == 3) {
                handlePattern3Args(synType, synonymName, args.get(0), args.get(1), args.get(2), partial);
            } else {
                throw new IllegalArgumentException("Invalid number of arguments in pattern clause for synonym: " + synonymName);
            }
        }
    }

    private List<String> splitPatternArgs(String argsRaw) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < argsRaw.length(); i++) {
            char c = argsRaw.charAt(i);

            if (c == ',' && depth == 0) {
                args.add(current.toString().trim());
                current.setLength(0);
            } else {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            args.add(current.toString().trim());
        }

        return args;
    }

    private void handlePattern2Args(SynonymType synType, String synName, String arg1, String arg2, Map<String, Set<String>> partial) {
        if (synType != SynonymType.ASSIGN && synType != SynonymType.WHILE) return;

        Set<String> result = new HashSet<>();

        if (synType == SynonymType.ASSIGN) {
            Set<Integer> candidates;

            if (!arg1.equals("_")) {
                String lhsVar = arg1.replaceAll("\"", "");
                candidates = pkb.getAssignsWithLhs(lhsVar);
            } else {
                candidates = pkb.getAllStmts().stream()
                        .filter(stmt -> pkb.getStmtType(stmt) == EntityType.ASSIGN)
                        .collect(Collectors.toSet());
            }

            for (Integer stmt : candidates) {
                TNode rhsTree = pkb.getAssignRhsTree(stmt);

                if (arg2.equals("_")) { // "_"
                    result.add(String.valueOf(stmt));
                } else if (arg2.startsWith("_\"") && arg2.endsWith("\"_")) { // _"..."_
                    String subExpr = arg2.substring(2, arg2.length() - 2);
                    TNode patternTree = ExpressionParser.parse(subExpr);
                    if (pkb.containsTopLevelSubtree(rhsTree, patternTree)) {
                        result.add(String.valueOf(stmt));
                    }
                } else { // "..."
                    String exactExpr = arg2.replaceAll("\"", "");
                    TNode patternTree = ExpressionParser.parse(exactExpr);
                    if (pkb.treesEqual(rhsTree, patternTree)) {
                        result.add(String.valueOf(stmt));
                    }
                }
            }

            partial.put(synName, result);
        }

        if (synType == SynonymType.WHILE) {
            Set<Integer> allWhileStmts = pkb.getAllStmts().stream()
                    .filter(stmt -> pkb.getStmtType(stmt) == EntityType.WHILE)
                    .collect(Collectors.toSet());

            if (!arg1.equals("_")) {
                String controlVar = arg1.replaceAll("\"", "");
                Set<Integer> filtered = allWhileStmts.stream()
                        .filter(stmt -> pkb.getWhileControlVars(stmt).contains(controlVar))
                        .collect(Collectors.toSet());
                partial.put(synName, toStringSet(filtered));
            } else {
                partial.put(synName, toStringSet(allWhileStmts));
            }
        }
    }

    private Set<String> toStringSet(Set<Integer> ints) {
        return ints.stream().map(String::valueOf).collect(Collectors.toSet());
    }

    private void handlePattern3Args(SynonymType synType, String synName, String arg1, String arg2, String arg3, Map<String, Set<String>> partial) {}
}
