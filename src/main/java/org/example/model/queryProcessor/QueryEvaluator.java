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
            }
        } catch (IllegalArgumentException e) {
            return "#Query is not valid";
        }

        synonyms = validator.getSynonyms();
        String[] split = query.split(";");
        String rawTail = split[split.length - 1].trim();
        String processed = toUpperCaseOutsideQuotes(rawTail);
        String selectRaw = rawTail.split("(?i)\\bSELECT\\b")[1]
                .split("(?i)\\bSUCH\\s+THAT\\b|(?i)\\bWITH\\b|(?i)\\bPATTERN\\b")[0].trim();
        boolean isTuple = selectRaw.startsWith("<") && selectRaw.endsWith(">");
        if (isTuple) selectRaw = selectRaw.substring(1, selectRaw.length() - 1).trim();
        boolean isBoolResult = selectRaw.equalsIgnoreCase("BOOLEAN");
        Set<String> selectCols = Arrays.stream(selectRaw.split("\\s*,\\s*"))
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Map<String, String>> tuples;
        try {
            tuples = processQuery(processed, rawTail, synonyms, isBoolResult);
        } catch (Exception e) {
            return "Error processing query";
        }

        if (isBoolResult) return (tuples != null && !tuples.isEmpty()) ? "true" : "false";
        if (tuples == null || tuples.isEmpty()) return "none";

        if (selectCols.size() == 1) {
            String col = selectCols.iterator().next();
            Set<String> vals = tuples.stream()
                    .map(row -> row.get(col))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return vals.isEmpty() ? "none" : String.join(", ", vals);
        }

        List<String> lines = new ArrayList<>();
        for (Map<String, String> row : tuples) {
            List<String> parts = selectCols.stream().map(row::get).collect(Collectors.toList());
            lines.add(String.join(" ", parts));
        }
        return String.join(", ", new LinkedHashSet<>(lines));
    }

    private List<Map<String, String>> joinTuples(List<Map<String, String>> leftTuples, Set<Pair<String, String>> rightPairs, String synA, String synB) {
        boolean sameSyn = synA.equalsIgnoreCase(synB);
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> row : leftTuples) {
            for (Pair<String, String> p : rightPairs) {
                String a = p.getFirst();
                String b = p.getSecond();
                if (sameSyn && !a.equals(b)) continue;
                boolean okA = (!row.containsKey(synA) || row.get(synA).equals(a));
                boolean okB = (!row.containsKey(synB) || row.get(synB).equals(b));
                if (!okA || !okB) continue;
                Map<String, String> copy = new HashMap<>(row);
                if (synonymsContain(synA)) copy.put(synA, a);
                if (synonymsContain(synB)) copy.put(synB, b);
                out.add(copy);
            }
        }
        return out;
    }

    private void filterByColumnType(String columnName, Set<String> values) {
        SynonymType type = synonyms.stream()
                .filter(s -> s.name().equalsIgnoreCase(columnName))
                .map(Synonym::type)
                .findFirst()
                .orElse(null);
        if (type != null) {
            Set<EntityType> allowed = mapSynonimToEntities(type);
            if (allowed != null) {
                values.removeIf(v -> {
                    try {
                        return !allowed.contains(pkb.getEntityType(v));
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
            case STMT -> Set.of(EntityType.IF, EntityType.WHILE, EntityType.CALL, EntityType.ASSIGN);
            case VARIABLE -> Set.of(EntityType.VARIABLE);
            case PROCEDURE -> Set.of(EntityType.PROCEDURE);
            case PROG_LINE -> Set.of(EntityType.IF, EntityType.WHILE, EntityType.CALL, EntityType.ASSIGN);
            default -> null;
        };
    }

    private List<Map<String, String>> processQuery(String processed, String raw, Set<Synonym> synonyms, boolean isBool) {
        List<Relationship> relationships = extractRelationships(processed);
        List<PatternClause> pcs = parsePatternClauses(raw);
        List<WithClause> wcs = parseWithClauses(raw);
        List<Map<String, String>> tuples = new ArrayList<>();
        tuples.add(new HashMap<>());

        if (relationships.isEmpty() && pcs.isEmpty() && !wcs.isEmpty()) {
            LinkedHashSet<String> withSyns = new LinkedHashSet<>();
            for (WithClause w : wcs) {
                String[] lp = w.left().split("\\.");
                withSyns.add(lp[0].toUpperCase());
                String right = w.right().trim();
                if (!right.matches("\\d+") && !right.matches("\".*\"")) {
                    String[] rp = right.split("\\.");
                    withSyns.add(rp[0].toUpperCase());
                }
            }
            for (String syn : withSyns) {
                Set<String> dom = domain(getSynType(syn));
                List<Map<String, String>> expanded = new ArrayList<>();
                for (Map<String, String> row : tuples) {
                    for (String v : dom) {
                        Map<String, String> copy = new HashMap<>(row);
                        copy.put(syn, v);
                        expanded.add(copy);
                    }
                }
                tuples = expanded;
            }
        }

        for (Relationship rel : relationships) {
            Set<Pair<String, String>> pairs = buildPairsFor(rel, synonyms);
            tuples = joinTuples(tuples, pairs, rel.getFirstArg().toUpperCase(), rel.getSecondArg().toUpperCase());
            if (tuples.isEmpty()) return Collections.emptyList();
        }

        if (relationships.isEmpty() && !pcs.isEmpty()) {
            String syn = pcs.get(0).synonym.toUpperCase();
            Set<String> dom = pkb.getAllStmts().stream()
                    .filter(s -> pkb.getEntityType(s) == EntityType.ASSIGN)
                    .map(String::valueOf)
                    .collect(Collectors.toSet());
            tuples.clear();
            for (String v : dom) {
                Map<String, String> m = new HashMap<>();
                m.put(syn, v);
                tuples.add(m);
            }
        }

        for (PatternClause pc : pcs) {
            Set<String> matches = buildPatternMatches(pc);
            String syn = pc.synonym.toUpperCase();
            tuples.removeIf(row -> {
                String val = row.get(syn);
                return val == null || !matches.contains(val);
            });
            if (tuples.isEmpty()) break;
        }

        Set<String> boundRelPat = new HashSet<>();
        for (Relationship r : relationships) {
            boundRelPat.add(r.getFirstArg().toUpperCase());
            boundRelPat.add(r.getSecondArg().toUpperCase());
        }
        for (PatternClause pc : pcs) boundRelPat.add(pc.synonym.toUpperCase());

        List<String> seedWithSyns = new ArrayList<>();
        for (WithClause w : wcs) {
            String synL = w.left().split("\\.")[0].toUpperCase();
            if (!boundRelPat.contains(synL)) seedWithSyns.add(synL);
            String right = w.right().trim();
            if (!right.matches("\\d+") && !right.matches("\".*\"")) {
                String synR = right.contains(".") ? right.split("\\.")[0].toUpperCase() : right.toUpperCase();
                if (!boundRelPat.contains(synR)) seedWithSyns.add(synR);
            }
        }
        for (String syn : seedWithSyns) {
            Set<String> dom = domain(getSynType(syn));
            List<Map<String, String>> expanded = new ArrayList<>();
            for (Map<String, String> row : tuples) {
                for (String val : dom) {
                    Map<String, String> copy = new HashMap<>(row);
                    copy.put(syn, val);
                    expanded.add(copy);
                }
            }
            tuples = expanded;
        }

        for (WithClause w : wcs) {
            String left = w.left().trim();
            String right = w.right().trim();
            String[] lp = left.split("\\.");
            String synL = lp[0].toUpperCase();
            boolean isLiteral = right.matches("\\d+") || right.matches("\".*\"");
            String literalVal = isLiteral ? right.replaceAll("^\"|\"$", "") : null;
            boolean isAttrCompare = !isLiteral && (right.contains(".") || synonymsContain(right));
            String synR = isAttrCompare ? (right.contains(".") ? right.split("\\.")[0].toUpperCase() : right.toUpperCase()) : null;
            tuples.removeIf(row -> {
                String valL = row.get(synL);
                if (valL == null) return true;
                if (isLiteral) return !valL.equals(literalVal);
                else if (isAttrCompare) {
                    String valR = row.get(synR);
                    return valR == null || !valL.equals(valR);
                }
                return true;
            });
            if (tuples.isEmpty()) break;
        }

        Set<String> bound = new HashSet<>();
        for (Relationship r : relationships) {
            bound.add(r.getFirstArg().toUpperCase());
            bound.add(r.getSecondArg().toUpperCase());
        }
        for (PatternClause pc : pcs) bound.add(pc.synonym.toUpperCase());
        for (WithClause w : wcs) {
            bound.add(w.left().split("\\.")[0].toUpperCase());
            String[] rp = w.right().split("\\.");
            if (rp.length >= 1) bound.add(rp[0].toUpperCase());
        }

        for (Synonym syn : synonyms) {
            String name = syn.name().toUpperCase();
            if (bound.contains(name)) continue;
            Set<String> dom = domain(syn.type());
            List<Map<String, String>> expanded = new ArrayList<>();
            for (Map<String, String> row : tuples) {
                for (String v : dom) {
                    Map<String, String> copy = new HashMap<>(row);
                    copy.put(name, v);
                    expanded.add(copy);
                }
            }
            tuples = expanded;
        }
        return tuples;
    }

    private Set<Pair<String, String>> buildPairsFor(Relationship rel, Set<Synonym> synonyms) {
        String left = rel.getFirstArg();
        String right = rel.getSecondArg();
        Map<String, Set<String>> relations = getRawRelation(rel.getType());
        Set<String> keys = new HashSet<>(relations.keySet());
        if (synonymsContain(left, synonyms)) filterByColumnType(left, keys);
        else if (!"_".equals(left)) keys.retainAll(Set.of(left.replace("\"", "")));
        relations.keySet().retainAll(keys);
        for (String k : new HashSet<>(relations.keySet())) {
            Set<String> vals = new HashSet<>(relations.get(k));
            if (synonymsContain(right, synonyms)) filterByColumnType(right, vals);
            else if (!"_".equals(right)) vals.retainAll(Set.of(right.replace("\"", "")));
            if (vals.isEmpty()) relations.remove(k); else relations.put(k, vals);
        }
        Set<Pair<String, String>> pairs = new HashSet<>();
        for (Map.Entry<String, Set<String>> e : relations.entrySet()) {
            for (String v : e.getValue()) pairs.add(new Pair<>(e.getKey(), v));
        }
        return pairs;
    }


    private String attrVal(String raw,String attr){
        if(attr==null||attr.isEmpty()) return raw;
        return switch(attr.toLowerCase()){
            case "value","varname","procname" -> raw.replaceAll("^\"|\"$","");
            case "stmt#" -> raw;
            default -> raw;
        };
    }

    private Map<String, Set<String>> getRawRelation(RelationshipType type) {
        switch (type) {
            case MODIFIES -> {
                Map<String, Set<String>> m1 = new HashMap<>();
                for (Map.Entry<Integer, Set<String>> e : pkb.getModifiedByStmtMap().entrySet())
                    m1.put(String.valueOf(e.getKey()), new HashSet<>(e.getValue()));
                for (Map.Entry<String, Set<String>> e : pkb.getModifiedByProcMap().entrySet())
                    m1.put(e.getKey(), new HashSet<>(e.getValue()));
                return m1;
            }
            case USES -> {
                Map<String, Set<String>> m2 = new HashMap<>();
                for (Map.Entry<Integer, Set<String>> e : pkb.getAllUses().entrySet())
                    m2.put(String.valueOf(e.getKey()), new HashSet<>(e.getValue()));
                for (Map.Entry<String, Set<String>> e : pkb.getAllUsesProc().entrySet())
                    m2.put(e.getKey(), new HashSet<>(e.getValue()));
                return m2;
            }
            case CALLS -> {
                return new HashMap<>(pkb.getCallsMap());
            }
            case CALLS_STAR -> {
                Map<String, Set<String>> calls = new HashMap<>(pkb.getCallsMap());
                return generateTransitive(calls);
            }
            case PARENT, PARENT_STAR -> {
                Map<String, Set<String>> m3 = new HashMap<>();
                for (Map.Entry<Integer, Integer> e : pkb.getParentMap().entrySet())
                    m3.computeIfAbsent(String.valueOf(e.getValue()), k -> new HashSet<>()).add(String.valueOf(e.getKey()));
                if (type == RelationshipType.PARENT_STAR) m3 = generateTransitive(m3);
                return m3;
            }
            case FOLLOWS, FOLLOWS_STAR -> {
                Map<String, Set<String>> m4 = new HashMap<>();
                for (Map.Entry<Integer, Integer> e : pkb.getAllFollows().entrySet())
                    m4.computeIfAbsent(String.valueOf(e.getKey()), k -> new HashSet<>()).add(String.valueOf(e.getValue()));
                if (type == RelationshipType.FOLLOWS_STAR) m4 = generateTransitive(m4);
                return m4;
            }
            case NEXT, NEXT_STAR -> {
                Map<String, Set<String>> m5 = new HashMap<>();
                for (Map.Entry<Integer, Set<Integer>> e : pkb.getAllNext().entrySet())
                    m5.put(String.valueOf(e.getKey()), e.getValue().stream().map(String::valueOf).collect(Collectors.toSet()));
                if (type == RelationshipType.NEXT_STAR) m5 = generateTransitive(m5);
                return m5;
            }
            case AFFECTS -> {
                return pkb.getAffectsStringMap();
            }
            case AFFECTS_STAR -> {
                Map<String, Set<String>> aff = new HashMap<>(pkb.getAffectsStringMap());
                return generateTransitive(aff);
            }
            default -> {
                return Collections.emptyMap();
            }
        }
    }

    private Set<String> domain(SynonymType t) {
        return switch (t) {
            case STMT -> pkb.getAllStmts().stream().map(String::valueOf).collect(Collectors.toSet());
            case ASSIGN -> pkb.getAllStmts().stream().filter(s -> pkb.getEntityType(s) == EntityType.ASSIGN).map(String::valueOf).collect(Collectors.toSet());
            case WHILE -> pkb.getAllStmts().stream().filter(s -> pkb.getEntityType(s) == EntityType.WHILE).map(String::valueOf).collect(Collectors.toSet());
            case IF -> pkb.getAllStmts().stream().filter(s -> pkb.getEntityType(s) == EntityType.IF).map(String::valueOf).collect(Collectors.toSet());
            case CALL -> pkb.getAllStmts().stream().filter(s -> pkb.getEntityType(s) == EntityType.CALL).map(String::valueOf).collect(Collectors.toSet());
            case PROCEDURE -> new HashSet<>(pkb.getAllProcedures());
            case VARIABLE -> new HashSet<>(pkb.getAllVariables());
            case CONSTANT -> new HashSet<>(pkb.getAllConstants());
            default -> new HashSet<>();
        };
    }

    private List<Relationship> extractRelationships(String query) {
        List<Relationship> result = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\b(FOLLOWS\\*?|PARENT\\*?|CALLS\\*?|MODIFIES|USES|NEXT\\*?|AFFECTS\\*?)\\s*\\(([^,\\)]+)\\s*,\\s*([^\\)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        while (matcher.find()) {
            String relName = matcher.group(1).toUpperCase();
            String arg1 = matcher.group(2).trim();
            String arg2 = matcher.group(3).trim();
            RelationshipType type = parseRelationshipType(relName);
            if (type != null) result.add(new Relationship(type, arg1 + ", " + arg2));
        }
        return result;
    }

    private RelationshipType parseRelationshipType(String name) {
        return switch (name.toUpperCase()) {
            case "CALLS" -> RelationshipType.CALLS;
            case "CALLS*" -> RelationshipType.CALLS_STAR;
            case "PARENT" -> RelationshipType.PARENT;
            case "PARENT*" -> RelationshipType.PARENT_STAR;
            case "FOLLOWS" -> RelationshipType.FOLLOWS;
            case "FOLLOWS*" -> RelationshipType.FOLLOWS_STAR;
            case "MODIFIES" -> RelationshipType.MODIFIES;
            case "USES" -> RelationshipType.USES;
            case "NEXT" -> RelationshipType.NEXT;
            case "NEXT*" -> RelationshipType.NEXT_STAR;
            case "AFFECTS" -> RelationshipType.AFFECTS;
            case "AFFECTS*" -> RelationshipType.AFFECTS_STAR;
            default -> null;
        };
    }

    private Map<String, Set<String>> generateTransitive(Map<String, Set<String>> relations) {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : relations.entrySet())
            result.put(entry.getKey(), new HashSet<>(entry.getValue()));
        boolean changed;
        do {
            changed = false;
            for (String a : new HashSet<>(result.keySet())) {
                Set<String> direct = result.get(a);
                Set<String> toAdd = new HashSet<>();
                for (String b : direct) {
                    Set<String> next = result.get(b);
                    if (next != null) {
                        for (String c : next) if (!direct.contains(c)) {
                            toAdd.add(c);
                            changed = true;
                        }
                    }
                }
                direct.addAll(toAdd);
            }
        } while (changed);
        return result;
    }

    private boolean synonymsContain(String s) {
        for (Synonym syn : synonyms) if (syn.name().equalsIgnoreCase(s)) return true;
        return false;
    }

    private boolean synonymsContain(String s, Set<Synonym> syns) {
        for (Synonym syn : syns) if (syn.name().equalsIgnoreCase(s)) return true;
        return false;
    }

    private List<WithClause> parseWithClauses(String query) {
        String[] sections = query.split("(?i)\\bWITH\\b");
        List<WithClause> clauses = new ArrayList<>();
        for (int i = 1; i < sections.length; i++) {
            String[] parts = sections[i].split("(?i)\\bSUCH\\s+THAT\\b|(?i)\\bAND\\b|(?i)\\bSELECT\\b");
            for (String part : parts) {
                String[] eq = part.split("=");
                if (eq.length == 2) clauses.add(new WithClause(eq[0].trim(), eq[1].trim()));
            }
        }
        return clauses;
    }

    private static String toUpperCaseOutsideQuotes(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '"' && (i == 0 || input.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
                sb.append(ch);
            } else sb.append(inQuotes ? ch : Character.toUpperCase(ch));
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

    private static class PatternClause {
        String synonym;
        List<String> args;
        PatternClause(String s, List<String> a) {
            synonym = s;
            args = a;
        }
    }

    private List<PatternClause> parsePatternClauses(String query) {
        List<PatternClause> list = new ArrayList<>();
        Pattern p = Pattern.compile("pattern\\s+([A-Za-z][A-Za-z0-9]*)\\s*\\(([^\\)]*)\\)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(query);
        while (m.find()) {
            String syn = m.group(1);
            String inside = m.group(2);
            List<String> parts = splitArgs(inside);
            SynonymType t = getSynType(syn);
            if (t == SynonymType.ASSIGN || t == SynonymType.WHILE) {
                if (parts.size() != 2) throw new IllegalArgumentException();
            } else if (t == SynonymType.IF) {
                if (parts.size() != 3) throw new IllegalArgumentException();
                if (!parts.get(1).equals("_") || !parts.get(2).equals("_")) throw new IllegalArgumentException();
            } else throw new IllegalArgumentException();
            validateExprSpec(parts);
            list.add(new PatternClause(syn, parts));
        }
        return list;
    }

    private static List<String> splitArgs(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inQuotes = !inQuotes;
            if (c == ',' && !inQuotes) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else cur.append(c);
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static void validateExprSpec(List<String> parts) {
        int subexprCount = 0;
        for (String p : parts) {
            if (p.matches("_\".*\"_")) subexprCount++;
            if (p.matches("_\".*\"") || p.matches("\".*\"_")) throw new IllegalArgumentException();
        }
        if (subexprCount > 1) throw new IllegalArgumentException();
    }

    private Set<String> buildPatternMatches(PatternClause pc) {
        Set<String> result = new HashSet<>();
        SynonymType t = getSynType(pc.synonym);
        if (t == SynonymType.ASSIGN) {
            String varSpec = pc.args.get(0);
            String exprSpec = pc.args.get(1);
            boolean anyVar = varSpec.equals("_");
            boolean anyExpr = exprSpec.equals("_");
            boolean subexpr = exprSpec.matches("_\".*\"_");
            boolean exact = exprSpec.matches("\".*\"");
            String exprInner = subexpr ? exprSpec.substring(2, exprSpec.length() - 2) : exact ? exprSpec.substring(1, exprSpec.length() - 1) : null;
            TNode patternTree = (exact || subexpr) ? ExpressionParser.parse(exprInner) : null;

            for (Integer stmt : pkb.getAllStmts()) {
                if (pkb.getEntityType(stmt) != EntityType.ASSIGN) continue;
                TNode rhs = pkb.getAssignRhsTree(stmt);
                if (!anyVar) {
                    String lhsVar = varSpec.replaceAll("\"", "");
                    if (!pkb.getAssignsWithLhs(lhsVar).contains(stmt)) continue;
                }
                if (anyExpr) result.add(String.valueOf(stmt));
                else if (exact && pkb.treesEqual(rhs, patternTree)) result.add(String.valueOf(stmt));
                else if (subexpr && pkb.containsSubtree(rhs, patternTree)) result.add(String.valueOf(stmt));
            }
        } else if (t == SynonymType.WHILE) {
            String varSpec = pc.args.get(0);
            for (Integer stmt : pkb.getAllStmts()) {
                if (pkb.getEntityType(stmt) != EntityType.WHILE) continue;
                if (varSpec.equals("_")) {
                    result.add(String.valueOf(stmt));
                } else {
                    String v = varSpec.replaceAll("\"", "");
                    if (pkb.getUsedByStmt(stmt).contains(v)) result.add(String.valueOf(stmt));
                }
            }
        } else if (t == SynonymType.IF) {
            String varSpec = pc.args.get(0);
            for (Integer stmt : pkb.getAllStmts()) {
                if (pkb.getEntityType(stmt) != EntityType.IF) continue;
                if (varSpec.equals("_")) {
                    result.add(String.valueOf(stmt));
                } else {
                    String v = varSpec.replaceAll("\"", "");
                    if (pkb.getUsedByStmt(stmt).contains(v)) result.add(String.valueOf(stmt));
                }
            }
        }
        return result;
    }
}
