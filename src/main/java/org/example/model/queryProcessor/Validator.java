package org.example.model.queryProcessor;

import java.util.HashSet;
import java.util.Set;

class Validator {
    private Set<Synonym> synonyms;
    private String queryPattern;

    public Validator() {
        synonyms = new HashSet<>();
    }

    boolean isValid(String query) {
        query = separateQueryFromSynonyms(query.toUpperCase().trim());

        System.out.println(query);

        setQueryPattern();

        return query.matches(queryPattern);
    }

    private String separateQueryFromSynonyms(String query) {
        String[] split = query.split(";");
        if (split.length > 1) {
            for(int i = 0; i < split.length - 1; i++) {
                addSynonym(split[i].trim());
            }
        }
        return split[split.length - 1].trim();
    }

    private void addSynonym(String declaration) {
        String[] split = declaration.split(" ");
        SynonymType type = SynonymMapper.toSynonymType(split[0]);
        for(int i = 1; i < split.length; i++) {
            synonyms.add(new Synonym(type, split[i].replace(',', ' ').trim()));
        }
    }
    private void setQueryPattern() {
        StringBuilder sb = new StringBuilder();
        sb.append("^SELECT (");
        for(Synonym s : synonyms) {
            sb.append(s.getName()).append('|');
        }
        sb.append("BOOLEAN)(\\s*,\\s*(");
        for(Synonym s : synonyms) {
            sb.append(s.getName()).append('|');
        }
        sb.append("BOOLEAN))* ");
        sb.append("SUCH THAT");
        sb.append("$");
        System.out.println(sb);
        queryPattern = sb.toString();
    }
}
