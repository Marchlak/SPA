package org.example.model.queryProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

//todo finish
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
            System.err.println("Invalid query");
            return Collections.emptySet();
        }

        synonyms = validator.getSynonyms();

        String[] split = query.split(";");
        String queryToProcess = split[split.length - 1].trim().toUpperCase();

        return processQuery(queryToProcess);
    }

    private Set<String> processQuery(String query) {
        Set<String> result = new HashSet<>();

        List<Relationship> relationships = new ArrayList<>();
        if (query.contains("FOLLOWS")) {
            relationships = Stream.concat(relationships.stream(), extractRelationship(query, RelationshipType.FOLLOWS).stream()).toList();
        }
        if (query.contains("FOLLOWS*")) {
            relationships = Stream.concat(relationships.stream(), extractRelationship(query, RelationshipType.FOLLOWS_STAR).stream()).toList();
        }
        if (query.contains("PARENT")) {
            relationships = Stream.concat(relationships.stream(), extractRelationship(query, RelationshipType.PARENT).stream()).toList();
        }
        if (query.contains("PARENT*")) {
            relationships = Stream.concat(relationships.stream(), extractRelationship(query, RelationshipType.PARENT_STAR).stream()).toList();
        }
        if (query.contains("MODIFIES")) {
            relationships = Stream.concat(relationships.stream(), extractRelationship(query, RelationshipType.MODIFIES).stream()).toList();
        }
        if (query.contains("USES")) {
            relationships = Stream.concat(relationships.stream(), extractRelationship(query, RelationshipType.USES).stream()).toList();
        }

        for (Relationship r : relationships) {
            System.out.println(r.getType().getType() + " " + r.getFirstArg() + " " + r.getSecondArg());
        }


        return result;
    }

    private List<Relationship> extractRelationship(String query, RelationshipType type) {
        List<Relationship> relationships = new ArrayList<>();

        String[] split = query.split(type.getType());
        for(int i = 1; i < split.length; i++) {
            relationships.add(new Relationship(type, extractRelationshipArgs(split[i])));
        }
        return relationships;
    }

    private String extractRelationshipArgs(String string) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(string.split("\\)")[0].trim());
        stringBuilder.deleteCharAt(0);
        return stringBuilder.toString();
    }

}
