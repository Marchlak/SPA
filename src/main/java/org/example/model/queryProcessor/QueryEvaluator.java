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

        String selectRaw = rawTail.split("(?i)\\bSELECT\\b")[1]
                .split("(?i)\\bSUCH\\s+THAT\\b|(?i)\\bWITH\\b|(?i)\\bPATTERN\\b")[0]
                .trim();
        boolean isBoolResult = selectRaw.trim().toUpperCase().startsWith("BOOLEAN");

        Map<String, Set<String>> solutions;
        try {
            solutions = processQuery(processed, rawTail, synonyms, isBoolResult);
        }
        catch (Exception e) {
            return "Error processing query";
        }

        if (isBoolResult) {
            return solutions == null ? "false" : "true";
        }
        else {
            if (solutions == null) { return "none"; }
        }

        //String resultString = buildResult(rawTail, solutions);

        Set<String> selectSynonims = new HashSet<>();
        selectSynonims.add(selectRaw);
        selectSynonims = selectSynonims.stream().map(String::toUpperCase).collect(Collectors.toSet());

        String resultString = buildResultNew(selectSynonims, solutions, false);

        return resultString;
    }

    private String buildResultNew(Set<String> tupleSynonimSymbols, Map<String, Set<String>> solutions, boolean isTuple) {
        List<String> allColumns = new ArrayList<>(solutions.keySet());
        List<List<String>> cartesian = buildCartesian(solutions);
        if (cartesian == null || cartesian.isEmpty()) {
            return "none";
        }

        List<Integer> selectedIndices = allColumns.stream()
                .filter(tupleSynonimSymbols::contains)
                .map(allColumns::indexOf)
                .toList();

        Set<String> uniqueRows = new LinkedHashSet<>();
        for (List<String> row : cartesian) {
            List<String> filteredRow = new ArrayList<>();
            for (int index : selectedIndices) {
                filteredRow.add(row.get(index));
            }
            uniqueRows.add(String.join(" ", filteredRow));
        }

        if (!isTuple) {
            return String.join(", ", uniqueRows);
        }

        return String.join("\n", uniqueRows);
    }

    private List<List<String>> buildCartesian(Map<String, Set<String>> solutions) {
        List<String> keys = new ArrayList<>(solutions.keySet());
        List<List<String>> cartesian = new ArrayList<>();
        cartesian.add(new ArrayList<>());

        for (String key : keys) {
            Set<String> values = solutions.get(key);
            if (values == null || values.isEmpty()) {
                return null;
            }

            List<List<String>> newCartesian = new ArrayList<>();
            for (List<String> row : cartesian) {
                for (String value : values) {
                    List<String> newRow = new ArrayList<>(row);
                    newRow.add(value);
                    newCartesian.add(newRow);
                }
            }
            cartesian = newCartesian;
        }
        return cartesian;
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
            filterByColumnType(col, values);

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

    private void filterByColumnType(String columnName, Set<String> values) {
        SynonymType type = synonyms.stream()
                .filter(s -> s.name().equalsIgnoreCase(columnName))
                .map(Synonym::type)
                .findFirst()
                .orElse(null);

        if (type != null) {
            Set<EntityType> allowedEntities = mapSynonimToEntities(type);

            if (allowedEntities != null) {
                values.removeIf(v -> {
                    try {
                        return !allowedEntities.contains(pkb.getEntityType(v));
                    } catch (NumberFormatException e) {
                        return true;
                    }
                });
            }
        }
    }

    private Set<EntityType> mapSynonimToEntities(SynonymType synonymType) {
        return switch (synonymType) {
            case ASSIGN -> Set.of(EntityType.ASSIGN);
            case WHILE -> Set.of(EntityType.WHILE);
            case IF -> Set.of(EntityType.IF);
            case CALL -> Set.of(EntityType.CALL);
            case STMT -> Set.of(EntityType.IF,
                    EntityType.WHILE,
                    EntityType.CALL,
                    EntityType.ASSIGN);
            case VARIABLE -> Set.of(EntityType.VARIABLE);
            case PROCEDURE -> Set.of(EntityType.PROCEDURE);
            case PROG_LINE -> Set.of(EntityType.IF,
                    EntityType.WHILE,
                    EntityType.CALL,
                    EntityType.ASSIGN);
            default -> null;
        };
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

    private Map<String, Set<String>> processQuery(String processed, String raw, Set<Synonym> synonims, boolean isBoolResult) {
        List<Relationship> relationships = extractRelationships(processed);
        Map<String, Set<String>> globalSynonimsResult = new HashMap<>();
        for (Synonym s : synonims) {
            Set<EntityType> allowedEntities = mapSynonimToEntities(s.type());
            globalSynonimsResult.put(s.name(), new HashSet<>());
            for (EntityType e : allowedEntities) {
                Set<String> strStmts = pkb.getStmtsByType(e);
                for (String stmt : strStmts) {
                    globalSynonimsResult.get(s.name()).add(stmt);
                }
            }
        }

        for (Relationship relation : relationships) {
            Map<String, Set<String>> relationSynonimsResult = new HashMap<>();
            applyRelationship(relation, relationSynonimsResult);

            for (Map.Entry<String, Set<String>> synonim : relationSynonimsResult.entrySet()) {
                if (synonymsContain(mapArgToSynonim(synonim.getKey(), relation))) {
                    Set<String> synonimValues = synonim.getValue();
                    Set<String> globalSynonimValues = globalSynonimsResult.get(mapArgToSynonim(synonim.getKey(), relation));
                    globalSynonimValues.retainAll(synonimValues);
                }
            }

            if (!hasSynonimResultPairs(relationSynonimsResult)) {
                return null;
            }
        }

        handlePatterns(raw, globalSynonimsResult);
        applyWithConstraints(raw, globalSynonimsResult);
        ensureAllSynonyms(globalSynonimsResult);
        return globalSynonimsResult;
    }

    private boolean hasSynonimResultPairs(Map<String, Set<String>> result) {
        if (result.isEmpty()) { return false; }
        for (Map.Entry<String, Set<String>> entry : result.entrySet()) {
            if (entry.getValue().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String mapArgToSynonim(String argName, Relationship relation) {
        return switch (argName) {
            case "arg0" -> relation.getFirstArg();
            case "arg1" -> relation.getSecondArg();
            default -> throw new IllegalArgumentException("Unknown argument " + argName);
        };
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
                    pkb.getAllStmts().stream().filter(s -> pkb.getEntityType(s) == EntityType.ASSIGN).map(String::valueOf).collect(Collectors.toSet());
            case WHILE ->
                    pkb.getAllStmts().stream().filter(s -> pkb.getEntityType(s) == EntityType.WHILE).map(String::valueOf).collect(Collectors.toSet());
            case IF ->
                    pkb.getAllStmts().stream().filter(s -> pkb.getEntityType(s) == EntityType.IF).map(String::valueOf).collect(Collectors.toSet());
            case CALL ->
                    pkb.getAllStmts().stream().filter(s -> pkb.getEntityType(s) == EntityType.CALL).map(String::valueOf).collect(Collectors.toSet());
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

    private Map<String, Set<String>> ensureKey(Map<String, Set<String>> map, String key) {
        map.computeIfAbsent(key, k -> new HashSet<>());
        return map;
    }

    private void handleCallsStar(String left, String right, Map<String, Set<String>> partialSolutions) {
        //Get all with that relations
        Map<String, Set<String>> relations = pkb.getCallsMap();

        relations = generateTransitive(relations);

        partialSolutions.clear();
        partialSolutions.putAll(handleRelation(left, right, relations));
    }

    private void handleCalls(String left, String right, Map<String, Set<String>> partialSolutions) {
        //Get all with that relations
        Map<String, Set<String>> relations = pkb.getCallsMap();

        partialSolutions.clear();
        partialSolutions.putAll(handleRelation(left, right, relations));
    }

    private void handleParent(String left, String right, Map<String, Set<String>> partialSolutions) {
        //Get all with that relations
        Map<Integer, Integer> parentReversed = pkb.getParentMap();

        //Reversing map - in pkb is <child, parent>
        Map<Integer, Set<Integer>> relationsNotTyped = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : parentReversed.entrySet()) {
            Integer child = entry.getKey();
            Integer parent = entry.getValue();

            relationsNotTyped
                    .computeIfAbsent(parent, k -> new HashSet<>())
                    .add(child);
        }

        //Replacing map to string substitute
        Map<String, Set<String>> relations = substituteRelationFrom(relationsNotTyped);

        partialSolutions.clear();
        partialSolutions.putAll(handleRelation(left, right, relations));
    }

    private void handleParentStar(String left, String right, Map<String, Set<String>> partialSolutions) {
        //Get all with that relations
        Map<Integer, Integer> parentReversed = pkb.getParentMap();

        //Reversing map - in pkb is <child, parent>
        Map<Integer, Set<Integer>> relationsNotTyped = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : parentReversed.entrySet()) {
            Integer child = entry.getKey();
            Integer parent = entry.getValue();

            relationsNotTyped
                    .computeIfAbsent(parent, k -> new HashSet<>())
                    .add(child);
        }

        //Replacing map to string substitute
        Map<String, Set<String>> relations = substituteRelationFrom(relationsNotTyped);

        //Star algorithm
        relations = generateTransitive(relations);

        partialSolutions.clear();
        partialSolutions.putAll(handleRelation(left, right, relations));
    }

    private void handleFollows(String left, String right, Map<String, Set<String>> partialSolutions) {
        //Get all with that relations
        Map<Integer, Integer> parentMap = pkb.getAllFollows();

        //Replacing map to string substitute
        Map<String, Set<String>> relations = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : parentMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String value = String.valueOf(entry.getValue());

            relations.computeIfAbsent(key, k -> new HashSet<>()).add(value);
        }

        partialSolutions.clear();
        partialSolutions.putAll(handleRelation(left, right, relations));
    }

    private void handleFollowsStar(String left, String right, Map<String, Set<String>> partialSolutions) {
        //Get all with that relations
        Map<Integer, Integer> parentMap = pkb.getAllFollows();

        //Replacing map to string substitute
        Map<String, Set<String>> relations = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : parentMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String value = String.valueOf(entry.getValue());

            relations.computeIfAbsent(key, k -> new HashSet<>()).add(value);
        }

        //Star algorithm
        relations = generateTransitive(relations);

        partialSolutions.clear();
        partialSolutions.putAll(handleRelation(left, right, relations));
    }

    private void handleNext(String left, String right, Map<String, Set<String>> partialSolutions) {
        //Get all with that relations
        Map<Integer, Set<Integer>> nextMap = pkb.getAllNext();

        //Replacing map to string substitute
        Map<String, Set<String>> relations = new HashMap<>();
        for (Map.Entry<Integer, Set<Integer>> entry : nextMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Set<Integer> integers = entry.getValue();
            Set<String> value = integers.stream().map(String::valueOf).collect(Collectors.toSet());

            relations.computeIfAbsent(key, k -> value);
        }

        partialSolutions.clear();
        partialSolutions.putAll(handleRelation(left, right, relations));
    }

    private void handleNextStar(String left, String right, Map<String, Set<String>> partialSolutions) {
        //Get all with that relations
        Map<Integer, Set<Integer>> nextMap = pkb.getAllNext();

        //Replacing map to string substitute
        Map<String, Set<String>> relations = new HashMap<>();
        for (Map.Entry<Integer, Set<Integer>> entry : nextMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Set<Integer> integers = entry.getValue();
            Set<String> value = integers.stream().map(String::valueOf).collect(Collectors.toSet());

            relations.computeIfAbsent(key, k -> value);
        }

        //Star algorithm
        relations = generateTransitive(relations);

        partialSolutions.clear();
        partialSolutions.putAll(handleRelation(left, right, relations));
    }

    private Map<String, Set<String>> substituteRelationFrom(Map<Integer, Set<Integer>> relations) {
        return relations.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> e.getValue().stream()
                                .map(String::valueOf)
                                .collect(Collectors.toSet())
                ));
    }

    private Map<String, Set<String>> generateTransitive(Map<String, Set<String>> relations) {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : relations.entrySet()) {
            result.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        boolean changed;

        do {
            changed = false;

            for (String a : new HashSet<>(result.keySet())) {
                Set<String> directChildren = result.get(a);
                Set<String> toAdd = new HashSet<>();

                for (String b : directChildren) {
                    Set<String> bChildren = result.get(b);
                    if (bChildren != null) {
                        for (String c : bChildren) {
                            if (!directChildren.contains(c)) {
                                toAdd.add(c);
                                changed = true;
                            }
                        }
                    }
                }

                directChildren.addAll(toAdd);
            }

        } while (changed);

        return result;
    }

    /**
     * Universal method for handling relation
     *
     * @param left Left synonim of the relation like "s" or "3".
     * @param right Right synonim of the relation like "w".
     * @param relations Map symbolising the relation like { "1" = ["2", "3"], "4" = ["5"] }
     * @return Map with the result of that relation like { "w" = ["10"], "s" = [] }
     */
    private Map<String, Set<String>> handleRelation(String left, String right, Map<String, Set<String>> relations) {
        Map<String, Set<String>> partialSolutions = new HashMap<>();

        //When left is synonim filter relations keys by their type
        if (synonymsContain(left)) {
            Set<String> keys = relations.keySet();
            filterByColumnType(left, keys);
            relations.keySet().removeIf(k -> !keys.contains(k));
        }
        else if (Objects.equals(left, "_")) { }
        //When left is numeral filter relations keys - must have same number
        else {
            relations.keySet().removeIf(k -> !k.equals(left.replace("\"", "")));
        }

        //When right is synonim type filter values by their type
        if (synonymsContain(right)) {
            for (Map.Entry<String, Set<String>> entry : relations.entrySet()) {
                Set<String> modifiableValues = new HashSet<>(entry.getValue());

                filterByColumnType(right, modifiableValues);
                entry.setValue(modifiableValues);
            }
        }
        else if (Objects.equals(right, "_")) { }
        //When right is numeral type values for keys must have same number
        else {
            for (Map.Entry<String, Set<String>> entry : relations.entrySet()) {

                Set<String> modifiableValues = new HashSet<>(entry.getValue());

                modifiableValues.removeIf(k -> !k.equals(right.replace("\"", "")));
                entry.setValue(modifiableValues);
            }
        }
        relations.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        partialSolutions.put("arg0", relations.keySet());
        partialSolutions.put("arg1", relations.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));

        return partialSolutions;
    }


    private void handleModifies(String left, String right, Map<String, Set<String>> partialSolutions) {
        //Get all with that relations
        Map<Integer, Set<String>> nextMap = pkb.getModifiedByStmtMap();

        //Replacing map to string substitute
        Map<String, Set<String>> relations = new HashMap<>();
        for (Map.Entry<Integer, Set<String>> entry : nextMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            relations.computeIfAbsent(key, k -> entry.getValue());
        }
        relations.putAll(pkb.getModifiedByProcMap());

        partialSolutions.clear();
        partialSolutions.putAll(handleRelation(left, right, relations));
    }

    private void handleUses(String left, String right, Map<String, Set<String>> partialSolutions) {
        //Get all with that relations
        Map<Integer, Set<String>> nextMap = pkb.getAllUses();

        //Replacing map to string substitute
        Map<String, Set<String>> relations = new HashMap<>();
        for (Map.Entry<Integer, Set<String>> entry : nextMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            relations.computeIfAbsent(key, k -> entry.getValue());
        }
        relations.putAll(pkb.getAllUsesProc());

        partialSolutions.clear();
        partialSolutions.putAll(handleRelation(left, right, relations));
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
                                return pkb.getEntityType(Integer.parseInt(v)) == filter;
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
                        .filter(s -> pkb.getEntityType(s) == EntityType.ASSIGN)
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
                    .filter(s -> pkb.getEntityType(s) == EntityType.WHILE)
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
                if (pkb.getEntityType(s) != EntityType.IF) continue;
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
            if (pkb.getEntityType(ifStmt) != EntityType.IF) continue;
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
