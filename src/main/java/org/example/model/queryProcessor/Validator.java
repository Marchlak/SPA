package org.example.model.queryProcessor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Validator {
    private final Set<Synonym> synonyms = new HashSet<>();
    private String queryPattern;

    public boolean isValid(String query) {
        synonyms.clear();
        try {
            query = separateQueryFromSynonyms(query.toUpperCase().trim());
            buildPattern();
        } catch (Exception e) {
            return false;
        }
        return query.matches(queryPattern);
    }

    public Set<Synonym> getSynonyms() {
        return Collections.unmodifiableSet(synonyms);
    }

    private String separateQueryFromSynonyms(String q) {
        String[] parts = q.split(";");
        for (int i = 0; i < parts.length - 1; i++) addSynonym(parts[i].trim());
        return parts[parts.length - 1].trim();
    }

    private void addSynonym(String decl) {
        String[] split = decl.split("\\s+");
        String token = switch (split[0]) {
            case "VAR" -> "VARIABLE";
            case "PROC" -> "PROCEDURE";
            case "IF" -> "IF";
            default -> split[0];
        };
        SynonymType type = SynonymMapper.toSynonymType(token);
        for (int i = 1; i < split.length; i++) {
            String n = split[i].trim();
            if (!n.matches("^[A-Z][A-Z0-9]*$")) throw new IllegalArgumentException();
            synonyms.add(new Synonym(type, n));
        }
    }

    private void buildPattern() {
        String synAlt = synonyms.isEmpty() ? "" : String.join("|", synonyms.stream().map(Synonym::name).toList());
        String selAlt = (synAlt.isEmpty() ? "" : synAlt + '|') + "BOOLEAN";
        String select = "^SELECT\\s+(" + selAlt + ")(\\s*,\\s*(" + selAlt + "))*";
        String relAlt = relationsAlternation(synAlt);
        String relClause = "SUCH\\s+THAT\\s+(?:" + relAlt + ")(?:\\s+AND\\s+(?:" + relAlt + "))*";
        String attrAlt = attributesAlternation();
        String withClause = "WITH\\s+" + attrAlt + "(?:\\s+AND\\s+" + attrAlt + ")*";
        String patternClause = "PATTERN\\s+.+";
        queryPattern = select +
                "(?:\\s+(?:" + relClause + "|" + withClause + "|" + patternClause + "))*$";
    }

    private String relationsAlternation(String synAlt) {
        String stmt = synAlt + "|_|[0-9]+";
        String ent = synAlt + "|_|(\"[A-Z][A-Z0-9]*\")";
        return rel("MODIFIES", stmt, ent) + '|' +
                rel("MODIFIES", ent, ent) + '|' +
                rel("USES", stmt, ent) + '|' +
                rel("USES", ent, ent) + '|' +
                rel("PARENT", stmt, stmt) + '|' +
                rel("PARENT\\*", stmt, stmt) + '|' +
                rel("FOLLOWS", stmt, stmt) + '|' +
                rel("FOLLOWS\\*", stmt, stmt) + '|' +
                rel("NEXT", stmt, stmt) + '|' +
                rel("NEXT\\*", stmt, stmt) + '|' +
                rel("CALLS", ent, ent) + '|' +
                rel("CALLS\\*", ent, ent);
    }

    private String rel(String name, String left, String right) {
        return '(' + name + ")\\s*\\((" + left + ")\\s*,\\s*(" + right + ")\\)";
    }

    private String attributesAlternation() {
        if (synonyms.isEmpty()) return "(?:)";
        StringBuilder sb = new StringBuilder();
        for (Synonym s : synonyms) {
            switch (s.type()) {
                case STMT, ASSIGN, WHILE, IF -> sb.append(s.name()).append("\\.STMT#\\s*=\\s*[0-9]+|");
                case VARIABLE -> sb.append(s.name()).append("\\.VARNAME\\s*=\\s*\"[A-Z][A-Z0-9]*\"|");
                case CONSTANT -> sb.append(s.name()).append("\\.VALUE\\s*=\\s*\\d+|");
                case PROCEDURE -> sb.append(s.name()).append("\\.PROCNAME\\s*=\\s*\"[A-Z][A-Z0-9]*\"|");
            }
        }
        sb.deleteCharAt(sb.length() - 1);
        return "(?:" + sb + ')';
    }
}