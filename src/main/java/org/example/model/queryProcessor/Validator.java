package org.example.model.queryProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
        if (parts.length < 1) throw new IllegalArgumentException();
        for (int i = 0; i < parts.length - 1; i++) addSynonym(parts[i].trim());
        return parts[parts.length - 1].trim();
    }

    private void addSynonym(String decl) {
        String[] split = decl.split("[\\s,]+");
        String token = switch (split[0]) {
            case "VAR" -> "VARIABLE";
            case "PROC" -> "PROCEDURE";
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
        String select = "^SELECT\\s+(?:" + selAlt + ")(?:\\s*,\\s*(?:" + selAlt + "))*";

        String relAlt = relationsAlternation(synAlt);
        String relClause = "SUCH\\s+THAT\\s+(?:" + relAlt + ")(?:\\s+AND\\s+(?:" + relAlt + "))*";

        String patternClause = "PATTERN\\s+.+";

        String withClause = null;
        if (!synonyms.isEmpty()) {
            String attrAlt = attributesAlternation();
            withClause = "WITH\\s+(?:" + attrAlt + ")(?:\\s+AND\\s+(?:" + attrAlt + "))*";
        }

        StringBuilder clauseBuilder = new StringBuilder();
        clauseBuilder.append("(?:").append(relClause);
        if (withClause != null) clauseBuilder.append('|').append(withClause);
        clauseBuilder.append('|').append(patternClause).append(")");

        queryPattern = select + "(?:\\s+" + clauseBuilder + "*)*$";
    }

    private String relationsAlternation(String synAlt) {
        String stmt = synAlt.isEmpty() ? "_|[0-9]+" : synAlt + "|_|[0-9]+";
        String entPattern = "\\\"[A-Z][A-Z0-9]*\\\"";
        String ent = synAlt.isEmpty() ? "_|" + entPattern : synAlt + "|_|" + entPattern;
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
        return name + "\\s*\\((?:" + left + ")\\s*,\\s*(?:" + right + ")\\)";
    }

    private String attributesAlternation() {
        List<String> patterns = new ArrayList<>();
        for (Synonym s : synonyms) {
            switch (s.type()) {
                case STMT, ASSIGN, WHILE, IF ->
                        patterns.add(s.name() + "\\.STMT#\\s*=\\s*[0-9]+");
                case VARIABLE ->
                        patterns.add(s.name() + "\\.VARNAME\\s*=\\s*\\\"[A-Z][A-Z0-9]*\\\"");
                case CONSTANT ->
                        patterns.add(s.name() + "\\.VALUE\\s*=\\s*\\d+");
                case PROCEDURE ->
                        patterns.add(s.name() + "\\.PROCNAME\\s*=\\s*\\\"[A-Z][A-Z0-9]*\\\"");
            }
        }
        List<Synonym> numericSyns = synonyms.stream()
                .filter(s -> switch (s.type()) {
                    case STMT, ASSIGN, WHILE, IF, CONSTANT -> true;
                    default -> false;
                })
                .toList();
        for (Synonym s1 : numericSyns) {
            String attr1 = (s1.type() == SynonymType.CONSTANT) ? "VALUE" : "STMT#";
            for (Synonym s2 : numericSyns) {
                String attr2 = (s2.type() == SynonymType.CONSTANT) ? "VALUE" : "STMT#";
                patterns.add(s1.name() + "\\." + attr1 + "\\s*=\\s*" + s2.name() + "\\." + attr2);
                patterns.add(s1.name() + "\\." + attr1 + "\\s*=\\s*" + s2.name());
            }
        }
        return String.join("|", patterns);
    }
}
