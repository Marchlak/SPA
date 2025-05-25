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

    public String evaluateQuery(String query) {

        try {
            if (!validator.isValid(query)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            return "#Query is not valid";
        }

        synonyms = validator.getSynonyms();

        String[] split = query.split(";");
        String rawTail = split[split.length - 1].trim();
        String processed = toUpperCaseOutsideQuotes(rawTail);
        processed = transformQueryToApplyWith(processed);

        Map<String, Set<String>> solutions =
                processQuery(processed, rawTail);

        String resultString = buildResult(rawTail, solutions);

        return resultString;
    }


    private String buildResult(String queryTail,
                               Map<String, Set<String>> sol) {

        /* w buildResult() */
        String selectRaw = queryTail.split("(?i)\\bSELECT\\b")[1]
                .split("(?i)\\bSUCH\\s+THAT\\b|(?i)\\bWITH\\b|(?i)\\bPATTERN\\b")[0]
                .trim();

        if (selectRaw.trim().toUpperCase().startsWith("BOOLEAN")) {
            boolean ok = sol.containsKey("BOOLEAN")
                    && !sol.get("BOOLEAN").isEmpty()
                    && !sol.get("BOOLEAN").contains("none");
            return ok ? "true" : "false";
        }

        if (sol.values().stream().anyMatch(s -> s.contains("none")))
            return "none";

        List<String> cols = Arrays.stream(selectRaw.split("\\s*,\\s*|\\s+"))
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .toList();

        List<String> pieces = new ArrayList<>();

        for (String col : cols) {

            /* zbiór wartości dla kolumny */
            Set<String> values = new HashSet<>(sol.getOrDefault(col, Set.of()));
            if (values.isEmpty()) return "none";

            /* ----- filtr po typie (ASSIGN, WHILE, IF, CALL) -------------- */
            SynonymType type = synonyms.stream()
                    .filter(s -> s.name().equalsIgnoreCase(col))
                    .map(Synonym::type)
                    .findFirst()
                    .orElse(null);

            if (type != null) {
                EntityType stmtFilter = switch (type) {
                    case ASSIGN -> EntityType.ASSIGN;
                    case WHILE -> EntityType.WHILE;
                    case IF -> EntityType.IF;
                    case CALL -> EntityType.CALL;
                    default -> null;
                };

                if (stmtFilter != null) {
                    values = values.stream()
                            .filter(v -> {
                                try {
                                    return pkb.getStmtType(Integer.parseInt(v)) == stmtFilter;
                                } catch (NumberFormatException e) {     // nie-liczba
                                    return false;
                                }
                            })
                            .collect(Collectors.toSet());
                }
            }

            if (values.isEmpty()) return "none";

            /* ----- sortowanie numeryczno-leksykograficzne ---------------- */
            List<String> sorted = new ArrayList<>(values);
            sorted.sort((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                } catch (NumberFormatException e) {
                    return a.compareTo(b);
                }
            });

            pieces.add(String.join(", ", sorted));
        }

        /* ---------- 5. sklejamy kolumny --------------------------------- */
        return String.join(" | ", pieces);
    }


    private void applyWithConstraints(String raw, Map<String, Set<String>> m) {
        for (WithClause w : parseWithClauses(raw)) {

            String[] l = w.left().split("\\.");
            String[] r = w.right().split("\\.");

            if (l.length==2 && "stmt#".equalsIgnoreCase(l[1]) && isNumeric(w.right())) {
                String syn = canonical(l[0]);
                Set<String> v = Set.of(w.right());
                m.put(syn, v);  m.put(syn.toUpperCase(), v);
                continue;
            }

            if (l.length==2 && r.length==2
                    &&  "stmt#".equalsIgnoreCase(l[1])
                    &&  "value".equalsIgnoreCase(r[1])) {

                String stmtSyn   = canonical(l[0]);
                String constSyn  = canonical(r[0]);

                Set<String> leftDomain  = new HashSet<>(m
                        .getOrDefault(stmtSyn,  domain(getSynType(stmtSyn))));
                Set<String> rightDomain = new HashSet<>(m
                        .getOrDefault(constSyn, domain(getSynType(constSyn))));

                leftDomain.retainAll(rightDomain);
                rightDomain.retainAll(leftDomain);

                m.put(stmtSyn,  leftDomain);
                m.put(constSyn, rightDomain);
            }
        }
    }
    /* pomocniczo: */
    private String canonical(String syn) {
        return synonyms.stream()
                .map(Synonym::name)
                .filter(n -> n.equalsIgnoreCase(syn))
                .findFirst()
                .orElse(syn);
    }

    private String transformQueryToApplyWith(String query) {
        StringBuilder sb = new StringBuilder();
        List<String> afterSelect = new ArrayList<>();
        List<String> withSynonyms = extractSynonymsUsedInWith(query);

        String[] parted = query.split("SUCH THAT");
        sb.append(parted[0]);
        if (parted.length > 0) {
            sb.append("SUCH THAT");
        }
        for (int i = 1; i < parted.length; i++) {
            String[] withoutWith = parted[i].split("WITH");
            afterSelect.addAll(List.of(withoutWith[0].split(" |,|\\(|\\)")));
        }
        int relationshipArgsCounter = 0;
        for (String s : afterSelect) {
            if (s.isEmpty()) continue;
            if (relationshipArgsCounter > 0) {
                if (relationshipArgsCounter == 1) {
                    sb.append(", ");
                }
                if (withSynonyms.contains(s)) {
                    sb.append(returnWithValue(s, query));
                } else {
                    sb.append(s);
                }
                if (relationshipArgsCounter == 1) {
                    sb.append(")");
                }
                relationshipArgsCounter--;
            }

            if (s.equals("PARENT") || s.equals("PARENT*") ||
                    s.equals("FOLLOWS") || s.equals("FOLLOWS*") ||
                    s.equals("CALLS") || s.equals("CALLS*") ||
                    s.equals("MODIFIES") || s.equals("USES") ||
                    s.equals("NEXT") || s.equals("NEXT*")) {
                relationshipArgsCounter = 2;
                sb.append(" ").append(s).append(" (");
            }
        }
        return sb.toString();
    }

    private List<String> extractSynonymsUsedInWith(String q) {
        List<String> out = new ArrayList<>();
        for (WithClause w : parseWithClauses(q)) {
            out.add(w.left().split("\\.")[0].toUpperCase());
        }
        return out;
    }

    private String returnWithValue(String syn, String q) {
        for (WithClause w : parseWithClauses(q)) {
            if (w.left().split("\\.")[0].equalsIgnoreCase(syn)) {
                return w.right();
            }
        }
        return "";
    }

    private Map<String, Set<String>> processQuery(String processed, String raw) {
        List<Relationship> relationships = extractRelationships(processed);
        Map<String, Set<String>> partial = initSynonymMap();
        boolean first = true;

        for (Relationship r : relationships) {
            Map<String, Set<String>> local = initSynonymMap();
            applyRelationship(r, local);
            for (Map.Entry<String, Set<String>> e : local.entrySet()) {
                Set<String> tgt = ensureKey(partial, e.getKey()).get(e.getKey());
                if (first) tgt.addAll(e.getValue());
                else tgt.retainAll(e.getValue());
            }
            first = false;
        }

        handlePatterns(raw, partial);
        applyWithConstraints(raw, partial);
        ensureAllSynonyms(partial);
        return partial;
    }

    private void applyRelationship(Relationship r, Map<String, Set<String>> partial) {
        String left = r.getFirstArg();
        String right = r.getSecondArg();
        switch (r.getType()) {
            case NEXT -> handleNext(left, right, partial);
            case NEXT_STAR -> handleNextStar(left, right, partial);
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


    private Set<String> domain(SynonymType t) {
        return switch (t) {
            case STMT -> pkb.getAllStmts().stream().map(String::valueOf).collect(Collectors.toSet());
            case ASSIGN ->
                    pkb.getAllStmts().stream().filter(s -> pkb.getStmtType(s) == EntityType.ASSIGN).map(String::valueOf).collect(Collectors.toSet());
            case WHILE ->
                    pkb.getAllStmts().stream().filter(s -> pkb.getStmtType(s) == EntityType.WHILE).map(String::valueOf).collect(Collectors.toSet());
            case IF ->
                    pkb.getAllStmts().stream().filter(s -> pkb.getStmtType(s) == EntityType.IF).map(String::valueOf).collect(Collectors.toSet());
            case CALL ->
                    pkb.getAllStmts().stream().filter(s -> pkb.getStmtType(s) == EntityType.CALL).map(String::valueOf).collect(Collectors.toSet());
            case PROCEDURE -> new HashSet<>(pkb.getAllProcedures());
            case VARIABLE -> new HashSet<>(pkb.getAllVariables());
            case CONSTANT  -> new HashSet<>(pkb.getAllConstants());
            default -> new HashSet<>();
        };
    }


    private void ensureAllSynonyms(Map<String, Set<String>> map) {
        for (Synonym s : synonyms) {
            Set<String> dom = domain(s.type());
            String raw   = s.name();
            String upper = raw.toUpperCase();

            map.computeIfAbsent(raw,   k -> new HashSet<>(dom));
            map.computeIfAbsent(upper, k -> new HashSet<>(dom));
        }
    }

    private List<Relationship> extractRelationships(String query) {
        List<Relationship> result = new ArrayList<>();
        Pattern pattern = Pattern.compile(
                "\\b(FOLLOWS\\*?|PARENT\\*?|CALLS\\*?|MODIFIES|USES|NEXT\\*?)\\s*\\(([^,\\)]+)\\s*,\\s*([^\\)]+)\\)",
                Pattern.CASE_INSENSITIVE);
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
            case "NEXT" -> {
                return RelationshipType.NEXT;
            }
            case "NEXT*" -> {
                return RelationshipType.NEXT_STAR;
            }
            default -> {
                return null;
            }
        }
    }

    private Map<String, Set<String>> initSynonymMap() {
        return new HashMap<>();
    }


    private Map<String, Set<String>> ensureKey(Map<String, Set<String>> map, String key) {
        map.computeIfAbsent(key, k -> new HashSet<>());
        return map;
    }

    private void handleCallsStar(String left, String right, Map<String, Set<String>> partialSolutions) {
        String caller = left.replace("\"", "");
        String callee = right.replace("\"", "");

        if (synonymsContain(left) && synonymsContain(right)) {
            for (String proc : pkb.getCallsMap().keySet()) {
                Set<String> allCallees = pkb.getCallsStar(proc);
                for (String c : allCallees) {
                    ensureKey(partialSolutions, left).get(left).add(proc);
                    ensureKey(partialSolutions, right).get(right).add(c);
                }
            }
        } else if (isStringLiteral(left) && synonymsContain(right)) {
            Set<String> callees = pkb.getCallsStar(caller);
            ensureKey(partialSolutions, right).get(right).addAll(callees);
        } else if (isStringLiteral(right) && synonymsContain(left)) {
            for (String proc : pkb.getCallsMap().keySet()) {
                if (pkb.getCallsStar(proc).contains(callee)) {
                    ensureKey(partialSolutions, left).get(left).add(proc);
                }
            }
        }
    }

    private void handleParentStar(String left, String right, Map<String, Set<String>> partialSolutions) {

        if (isNumeric(right) && synonymsContain(left)) {
            int c = Integer.parseInt(right);
            Set<Integer> parents = pkb.getParentStar(c);
            if (parents.isEmpty()) {
                ensureKey(partialSolutions, left).get(left).add("none");
            } else {
                for (int p : parents)
                    ensureKey(partialSolutions, left).get(left).add(String.valueOf(p));
            }
        }
        if (isNumeric(left) && synonymsContain(right)) {
            int p = Integer.parseInt(left);
            Set<Integer> descendants = pkb.getParentedStarBy(p);
            if (descendants.isEmpty()) {
                ensureKey(partialSolutions, right).get(right).add("none");
            } else {
                for (int d : descendants)
                    ensureKey(partialSolutions, right).get(right).add(String.valueOf(d));
            }
        }
    }

    private void handleFollowsStar(String left, String right, Map<String, Set<String>> partial) {
        if (isNumeric(right) && synonymsContain(left)) {
            Set<String> set = ensureKey(partial, left).get(left);
            int r = Integer.parseInt(right);
            Set<Integer> preds = pkb.getFollowedByStar(r);
            if (preds.isEmpty()) {
                set.add("none");
            } else {
                for (int p : preds) set.add(String.valueOf(p));
            }
        }

        if (isNumeric(left) && synonymsContain(right)) {
            Set<String> set = ensureKey(partial, right).get(right);
            int l = Integer.parseInt(left);
            Set<Integer> succs = pkb.getFollowsStar(l);
            if (succs.isEmpty()) {
                set.add("none");
            } else {
                for (int s : succs) set.add(String.valueOf(s));
            }
        }

        if (synonymsContain(left) && synonymsContain(right)) {
            Set<String> lRes = new HashSet<>();
            Set<String> rRes = new HashSet<>();
            for (int s : pkb.getAllStmts()) {
                for (int t : pkb.getFollowsStar(s)) {
                    lRes.add(String.valueOf(s));
                    rRes.add(String.valueOf(t));
                }
            }
            if (lRes.isEmpty()) lRes.add("none");
            if (rRes.isEmpty()) rRes.add("none");
            partial.put(left, lRes);
            partial.put(right, rRes);
        }
    }

    private void handleCalls(String left, String right, Map<String, Set<String>> partialSolutions) {
        String caller = left.replace("\"", "");
        String callee = right.replace("\"", "");

        if (synonymsContain(left) && synonymsContain(right)) {
            for (Map.Entry<String, Set<String>> entry : pkb.getCallsMap().entrySet()) {
                String procCaller = entry.getKey();
                for (String procCallee : entry.getValue()) {
                    ensureKey(partialSolutions, left).get(left).add(procCaller);
                    ensureKey(partialSolutions, right).get(right).add(procCallee);
                }
            }
        } else if (isStringLiteral(left) && synonymsContain(right)) {
            Set<String> callees = pkb.getCalls(caller);
            ensureKey(partialSolutions, right).get(right).addAll(callees);
        } else if (isStringLiteral(right) && synonymsContain(left)) {
            for (Map.Entry<String, Set<String>> entry : pkb.getCallsMap().entrySet()) {
                if (entry.getValue().contains(callee)) {
                    ensureKey(partialSolutions, left).get(left).add(entry.getKey());
                }
            }
        }
    }

    private void handleParent(String left,
                              String right,
                              Map<String, Set<String>> partial) {

        if (synonymsContain(left) && synonymsContain(right)) {

            Set<String> lSet = partial.computeIfAbsent(left, k -> new HashSet<>());
            Set<String> rSet = partial.computeIfAbsent(right, k -> new HashSet<>());

            for (int parent : pkb.getAllStmts()) {
                for (int child : pkb.getParentedBy(parent)) {
                    lSet.add(String.valueOf(parent));
                    rSet.add(String.valueOf(child));
                }
            }

            if (lSet.isEmpty()) lSet.add("none");
            if (rSet.isEmpty()) rSet.add("none");
            return;
        }

        if (isNumeric(right) && synonymsContain(left)) {
            int c = Integer.parseInt(right);
            Integer p = pkb.getParent(c);
            ensureKey(partial, left).get(left)
                    .add(p == -1 ? "none" : String.valueOf(p));
            return;
        }

        if (isNumeric(left) && synonymsContain(right)) {
            int p = Integer.parseInt(left);
            Set<Integer> kids = pkb.getParentedBy(p);
            ensureKey(partial, right).get(right)
                    .addAll(kids.isEmpty()
                            ? Set.of("none")
                            : kids.stream().map(String::valueOf).toList());
        }
    }

    private void handleFollows(String left, String right, Map<String, Set<String>> partialSolutions) {
        if (isNumeric(right) && synonymsContain(left)) {
            handleFollowedBy(Integer.parseInt(right), left, partialSolutions);
        }

        if (isNumeric(left) && synonymsContain(right)) {
            handleFollowsOf(Integer.parseInt(left), right, partialSolutions);
        }
    }

    private void handleFollowedBy(int rightValue, String left, Map<String, Set<String>> partialSolutions) {
        Integer result = pkb.getFollowedBy(rightValue);
        ensureKey(partialSolutions, left);
        if (result != null) {
            partialSolutions.get(left).add(String.valueOf(result));
        }
    }

    private void handleFollowsOf(int leftValue, String right, Map<String, Set<String>> partialSolutions) {
        Integer result = pkb.getFollows(leftValue);
        ensureKey(partialSolutions, right);
        if (result != null) {
            partialSolutions.get(right).add(String.valueOf(result));
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
                    ensureKey(partial, left).get(left).add(String.valueOf(stmt));
                }
            }
            for (String proc : pkb.getAllProcedures()) {
                if (pkb.getModifiedByProc(proc).stream()
                        .anyMatch(v -> v.equalsIgnoreCase(rightLit))) {
                    ensureKey(partial, left).get(left).add(proc);
                }
            }
            return;
        }

        if (leftIsNum && synonymsContain(right)) {
            ensureKey(partial, right).get(right).addAll(pkb.getModifiedByStmt(Integer.parseInt(left)));
        }

        if (leftLit != null && synonymsContain(right)) {
            ensureKey(partial, right).get(right).addAll(pkb.getModifiedByProc(leftLit));
        }

        if (!leftIsNum && synonymsContain(left) && synonymsContain(right)) {
            for (String proc : pkb.getAllProcedures()) {
                for (String var : pkb.getModifiedByProc(proc)) {
                    ensureKey(partial, left).get(left).add(proc);
                    ensureKey(partial, right).get(right).add(var);
                }
            }
        }

        if (synonymsContain(left) && synonymsContain(right)) {
            for (int stmt : pkb.getAllStmts()) {
                for (String var : pkb.getModifiedByStmt(stmt)) {
                    ensureKey(partial, left).get(left).add(String.valueOf(stmt));
                    ensureKey(partial, right).get(right).add(var);
                }
            }
        }

        if (synonymsContain(left) && rightLit != null) {
            for (String proc : pkb.getAllProcedures()) {
                if (pkb.getModifiedByProc(proc).stream()
                        .anyMatch(v -> v.equalsIgnoreCase(rightLit))) {
                    ensureKey(partial, left).get(left).add(proc);
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
                    ensureKey(partial, left).get(left).add(String.valueOf(stmt));
                }
            }
            return;
        }

        if (leftIsNum && synonymsContain(right)) {
            ensureKey(partial, right).get(right).addAll(pkb.getUsedByStmt(Integer.parseInt(left)));
            return;
        }

        if (leftIsLit && synonymsContain(right)) {
            ensureKey(partial, right).get(right).addAll(pkb.getUsedByProc(leftLit));
            return;
        }

        if (isProcSyn(left) && synonymsContain(right)) {
            for (String proc : pkb.getAllProcedures()) {
                for (String var : pkb.getUsedByProc(proc)) {
                    ensureKey(partial, left).get(left).add(proc);
                    ensureKey(partial, right).get(right).add(var);
                }
            }
            return;
        }

        if (isStmtSyn(left) && synonymsContain(right)) {
            for (int stmt : pkb.getAllStmts()) {
                for (String var : pkb.getUsedByStmt(stmt)) {
                    ensureKey(partial, left).get(left).add(String.valueOf(stmt));
                    ensureKey(partial, right).get(right).add(var);
                }
            }
            return;
        }

        if (isProcSyn(left) && rightLit != null) {
            for (String proc : pkb.getAllProcedures()) {
                if (pkb.getUsedByProc(proc).contains(rightLit)) {
                    ensureKey(partial, left).get(left).add(proc);
                }
            }
        }
    }

    private boolean isStringLiteral(String s) {
        return s.startsWith("\"") && s.endsWith("\"");
    }

    private Set<String> finalizeResult(String query, Map<String, Set<String>> partialSolutions) {
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
                case WHILE -> EntityType.WHILE;
                case IF -> EntityType.IF;
                case CALL -> EntityType.CALL;
                default -> null;
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
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
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

    // --- replace the old method entirely -------------------------------
    private List<WithClause> parseWithClauses(String query) {

        /*  (?i)     – turn on case-insensitive matching
         *  \\bWITH\\b – split exactly on the word WITH
         */
        String[] sections = query.split("(?i)\\bWITH\\b");

        List<WithClause> clauses = new ArrayList<>();

        /*  Inside the WITH-part we must stop once we reach the next
         *  keyword (SUCH THAT / AND / SELECT); again all case-insensitive.
         */
        for (int i = 1; i < sections.length; i++) {
            String[] parts = sections[i]
                    .split("(?i)\\bSUCH\\s+THAT\\b|(?i)\\bAND\\b|(?i)\\bSELECT\\b");

            for (String part : parts) {
                String[] eq = part.split("=");
                if (eq.length == 2) {
                    clauses.add(new WithClause(eq[0].trim(), eq[1].trim()));
                }
            }
        }
        return clauses;
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
            Set<String> lSet = partial.computeIfAbsent(left, k -> new HashSet<>());
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
            Set<String> lSet = partial.computeIfAbsent(left, k -> new HashSet<>());
            Set<String> rSet = partial.computeIfAbsent(right, k -> new HashSet<>());
            for (int from : pkb.getAllStmts()) {
                for (int to : pkb.getNextStar(from)) {
                    lSet.add(String.valueOf(from));
                    rSet.add(String.valueOf(to));
                }
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

    private void handlePatterns(String query, Map<String, Set<String>> partial) {

        Pattern p = Pattern.compile(
                "pattern\\s+([A-Za-z][A-Za-z0-9]*)\\s*\\(([^()]*?(?:\\([^()]*\\)[^()]*)*?)\\)",
                Pattern.CASE_INSENSITIVE);

        Matcher m = p.matcher(query);
        while (m.find()) {
            String synName = m.group(1).trim();
            String argsRaw = m.group(2).trim();

            SynonymType synType = synonyms.stream()
                    .filter(s -> s.name().equalsIgnoreCase(synName))
                    .map(Synonym::type)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown synonym in PATTERN clause: " + synName));

            List<String> args = splitPatternArgs(argsRaw);

            if (args.size() == 2) {
                handlePattern2Args(synType, synName, args.get(0), args.get(1), partial);
            } else if (args.size() == 3) {
                handlePattern3Args(synType, synName, args.get(0), args.get(1), args.get(2), partial);
            } else {
                throw new IllegalArgumentException("Invalid number of arguments in pattern clause for synonym: " + synName);
            }
        }
    }

    private void handlePattern2Args(SynonymType synType,
                                    String synNameRaw,
                                    String arg1,
                                    String arg2,
                                    Map<String, Set<String>> partial) {

        String synKey = synNameRaw.toUpperCase();
        partial.computeIfAbsent(synKey, k -> new HashSet<>());

        if (synType == SynonymType.ASSIGN) {

            Set<String> result = new HashSet<>();
            Set<Integer> candidates;

            if (!arg1.equals("_")) {
                String lhsVar = arg1.replaceAll("\"", "");
                candidates = pkb.getAssignsWithLhs(lhsVar);
            } else {
                candidates = pkb.getAllStmts().stream()
                        .filter(s -> pkb.getStmtType(s) == EntityType.ASSIGN)
                        .collect(Collectors.toSet());
            }

            for (Integer stmt : candidates) {
                TNode rhsTree = pkb.getAssignRhsTree(stmt);

                if (arg2.equals("_")) {
                    result.add(String.valueOf(stmt));

                } else if (arg2.startsWith("_\"") && arg2.endsWith("\"_")) {
                    String subExpr = arg2.substring(2, arg2.length() - 2);
                    TNode patternTree = ExpressionParser.parse(subExpr);
                    if (pkb.containsTopLevelSubtree(rhsTree, patternTree))
                        result.add(String.valueOf(stmt));

                } else {
                    String exact = arg2.replaceAll("\"", "");
                    TNode patternTree = ExpressionParser.parse(exact);
                    if (pkb.treesEqual(rhsTree, patternTree))
                        result.add(String.valueOf(stmt));
                }
            }
            partial.put(synKey, result);
            if (!result.isEmpty())
                partial.computeIfAbsent("BOOLEAN", k -> new HashSet<>()).add("T");
            return;
        }

        if (synType == SynonymType.WHILE) {

            Set<Integer> allWhiles = pkb.getAllStmts().stream()
                    .filter(s -> pkb.getStmtType(s) == EntityType.WHILE)
                    .collect(Collectors.toSet());

            Set<Integer> matched;
            if (!arg1.equals("_")) {
                String var = arg1.replaceAll("\"", "");
                matched = allWhiles.stream()
                        .filter(s -> pkb.getWhileControlVars(s).contains(var))
                        .collect(Collectors.toSet());
            } else {
                matched = allWhiles;
            }

            partial.put(synKey, toStringSet(matched));
            if (!matched.isEmpty())
                partial.computeIfAbsent("BOOLEAN", k -> new HashSet<>()).add("T");
            return;
        }

        if (synType == SynonymType.IF) {

            if (arg1.equals("\"\"")) arg1 = "_";

            boolean any = arg1.equals("_");
            boolean lit = arg1.startsWith("\"") && arg1.endsWith("\"");
            boolean syn = synonymsContain(arg1);
            String  litVal = lit ? arg1.substring(1, arg1.length() - 1) : null;

            if (syn) partial.computeIfAbsent(arg1.toUpperCase(), k -> new HashSet<>());

            Set<String> matched = new HashSet<>();

            for (int s : pkb.getAllStmts()) {
                if (pkb.getStmtType(s) != EntityType.IF) continue;
                Set<String> ctrl = pkb.getIfControlVars(s);

                if (any) {
                    matched.add(String.valueOf(s));

                } else if (lit && ctrl.contains(litVal)) {
                    matched.add(String.valueOf(s));

                } else if (syn) {
                    for (String v : ctrl) {
                        matched.add(String.valueOf(s));
                        partial.get(arg1.toUpperCase()).add(v);
                    }
                }
            }
            partial.get(synKey).addAll(matched);
            if (!matched.isEmpty())
                partial.computeIfAbsent("BOOLEAN", k -> new HashSet<>()).add("T");
        }
    }

    private void handlePattern3Args(SynonymType synType,
                                    String synNameRaw,
                                    String arg1,
                                    String arg2,
                                    String arg3,
                                    Map<String, Set<String>> partial) {

        if (synType != SynonymType.IF) return;

        String synKey = synNameRaw.toUpperCase();
        partial.computeIfAbsent(synKey, k -> new HashSet<>());

        if (arg1.equals("\"\"")) arg1 = "_";

        boolean any = arg1.equals("_");
        boolean lit = arg1.startsWith("\"") && arg1.endsWith("\"");
        boolean syn = synonymsContain(arg1);

        if (syn) partial.computeIfAbsent(arg1.toUpperCase(), k -> new HashSet<>());

        String litVal = lit ? arg1.substring(1, arg1.length() - 1) : null;

        Set<String> matches = new HashSet<>();

        for (int ifStmt : pkb.getAllStmts()) {
            if (pkb.getStmtType(ifStmt) != EntityType.IF) continue;
            Set<String> ctrl = pkb.getIfControlVars(ifStmt);

            if (any) {
                matches.add(String.valueOf(ifStmt));

            } else if (lit && ctrl.contains(litVal)) {
                matches.add(String.valueOf(ifStmt));

            } else if (syn) {
                for (String v : ctrl) {
                    matches.add(String.valueOf(ifStmt));
                    partial.get(arg1.toUpperCase()).add(v);
                }
            }
        }

        partial.get(synKey).addAll(matches);
        if (!matches.isEmpty())
            partial.computeIfAbsent("BOOLEAN", k -> new HashSet<>()).add("T");
    }

    private Set<String> toStringSet(Set<Integer> ints) {
        return ints.stream().map(String::valueOf).collect(Collectors.toSet());
    }


}
