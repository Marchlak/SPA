package org.example.model.queryProcessor;

import java.util.*;
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
      return Collections.emptySet();
    }
    synonyms = validator.getSynonyms();
    String[] split = query.split(";");
    String queryToProcess = split[split.length - 1].trim().toUpperCase();
    Set<String> result = processQuery(queryToProcess);
    if(result.isEmpty()) result.add("none");
    return result;
  }

  private Set<String> processQuery(String query) {
    List<Relationship> relationships = extractRelationships(query);
    Map<String, Set<String>> partialSolutions = initSynonymMap();
    for (Relationship r : relationships) {
      RelationshipType t = r.getType();
      String left = r.getFirstArg();
      String right = r.getSecondArg();
      if (t == RelationshipType.CALLS)
        handleCalls(left, right, partialSolutions);
      else if (t == RelationshipType.CALLS_STAR)
        handleCallsStar(left, right, partialSolutions);
      if (t == RelationshipType.PARENT)
        handleParent(left, right, partialSolutions);
      if (t == RelationshipType.PARENT_STAR)
        handleParentStar(left, right, partialSolutions);
      if (t == RelationshipType.FOLLOWS)
        handleFollows(left, right, partialSolutions);
      if (t == RelationshipType.FOLLOWS_STAR){}
        handleFollowsStar(left, right, partialSolutions);
      if (t == RelationshipType.USES){}
      //handleUses(left, right, partialSolutions);
      if (t == RelationshipType.MODIFIES){}
      //handleModifies(left, right, partialSolutions);
    }
    return finalizeResult(query, partialSolutions);
  }

  private List<Relationship> extractRelationships(String query) {
    List<Relationship> result = new ArrayList<>();
    if (query.contains("Calls*")) {
      result.addAll(extractRelationship(query, RelationshipType.CALLS_STAR));
    }
    if (query.contains("Calls")) {
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
    return result;
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
    return args.replace("\"", "");
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
      if(i == -1){
        partialSolutions.get(left).add(String.valueOf("none"));
        return;
      }
      int p = (int)i;
      if (p > 0)
        partialSolutions.get(left).add(String.valueOf(p));
    }
    if (isNumeric(left) && synonymsContain(right)) {
      int p = Integer.parseInt(left);
      Set<Integer> kids = pkb.getParentedBy(p);
      if (kids.isEmpty()) {
        partialSolutions.get(right).add("none");
      }else {
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
      if(i == null) {
        partialSolutions.get(left).add(String.valueOf("none"));
        return;
      }
      int f = (int)i;
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
//  private void handleUses(String left, String right, Map<String, Set<String>> partialSolutions) {
//    if (isNumeric(left) && synonymsContain(right)) {
//      int stmt = Integer.parseInt(left);
//      Set<String> usedVars = pkb.getUsedByStmt(stmt);
//      partialSolutions.get(right).addAll(usedVars);
//    }
//    if (!isNumeric(left) && synonymsContain(left) && right.startsWith("\"") && right.endsWith("\"")) {
//    }
//  }
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
    String selectPart = query.split("SELECT")[1].trim().split("SUCH THAT|WITH")[0].trim();
    if (selectPart.contains(" "))
      selectPart = selectPart.split(" ")[0];
    Set<String> result = new HashSet<>();
    if (synonymsContain(selectPart)) {
      result.addAll(partialSolutions.get(selectPart));
    }
    return result.stream().filter(s -> !s.isBlank()).collect(Collectors.toSet());
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
}
