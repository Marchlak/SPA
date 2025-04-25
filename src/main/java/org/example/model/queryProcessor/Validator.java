package org.example.model.queryProcessor;

import java.util.HashSet;
import java.util.Set;
//todo in relationships only correct variables
class Validator {
    private final Set<Synonym> synonyms;
    private String queryPattern;

    public Validator() {
        synonyms = new HashSet<>();
    }

    boolean isValid(String query) {
        synonyms.clear();
        try {
            query = separateQueryFromSynonyms(query.toUpperCase().trim());

            setQueryPattern();
        } catch (Exception e) {
            return false;
        }
        return query.matches(queryPattern);
    }

    Set<Synonym> getSynonyms() {
        return synonyms;
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
        String synonymName;
        SynonymType type = SynonymMapper.toSynonymType(split[0]);
        for(int i = 1; i < split.length; i++) {
            synonymName = split[i].replace(',', ' ').trim();
            if(!synonymName.matches("^[A-Z][A-Z0-9#]*$")) {
                throw new IllegalArgumentException();
            }
            synonyms.add(new Synonym(type, synonymName));
        }
    }
    private void setQueryPattern() {
        queryPattern = "^SELECT\\s+(" +
                getSynonymsPattern() +
                "|BOOLEAN)(\\s?,\\s?(" +
                getSynonymsPattern() +
                "|BOOLEAN))*" +
                getSuchThatPattern() +
                getWithPattern() +
                "$";
    }

    private String getSynonymsPattern() {
        StringBuilder sb = new StringBuilder();
        for(Synonym s : synonyms) {
            sb.append(s.name()).append('|');
        }
        sb.delete(sb.length() - 1, sb.length());
        return sb.toString();
    }

    private String getSuchThatPattern() {
        return "(\\s+SUCH THAT\\s+(" +
                getRelRefPattern("MODIFIES") + "|" +
                getRelRefPattern("USES") + "|" +
                getRelRefPattern("PARENT") + "|" +
                getRelRefPattern("PARENT\\*") + "|" +
                getRelRefPattern("FOLLOWS") + "|" +
                getRelRefPattern("FOLLOWS\\*") + "|" +
                getRelRefPattern("CALLS") + "|" +
                getRelRefPattern("CALLS\\*") +
                "))?" +
                "(\\s+AND\\s+(" +
                getRelRefPattern("MODIFIES") + "|" +
                getRelRefPattern("USES") + "|" +
                getRelRefPattern("PARENT") + "|" +
                getRelRefPattern("PARENT\\*") + "|" +
                getRelRefPattern("FOLLOWS") + "|" +
                getRelRefPattern("FOLLOWS\\*") + "|" +
                getRelRefPattern("CALLS") + "|" +
                getRelRefPattern("CALLS\\*") +
                "))*";
    }

    private String getRelRefPattern(String rel) {
        StringBuilder sb = new StringBuilder();
        sb.append("(")
                .append(rel)
                .append("\\s+\\((")
                .append(getStmtRefPattern())
                .append(")\\s?,\\s?(");
        String s = rel.equals("MODIFIES") || rel.equals("USES") ? getEntRefPattern() : getStmtRefPattern();
        sb.append(s);
        sb.append("))\\)");
        return sb.toString();
    }

    private String getStmtRefPattern() {
        return getSynonymsPattern() +
                "|_|\\d+";
    }

    private String getEntRefPattern() {
        return getSynonymsPattern() + "|_|(\"[A-Z][A-Z0-9#]*\")|\\d+";
    }

    private String getWithPattern() {
        StringBuilder sb = new StringBuilder();
        sb.append("(\\s+WITH\\s+(");
        for (Synonym s : synonyms) {
            sb.append("(");
            switch (s.type()) {
                case STMT, ASSIGN, WHILE -> sb.append(getStmtAttributePattern(s.name()))
                        .append(")|");
                case VARIABLE -> sb.append(getVarNameAttributePattern(s.name()))
                        .append(")|");
                case CONSTANT -> sb.append(getValueAttributePattern(s.name()))
                        .append(")|");
                case PROCEDURE -> sb.append(getProcNameAttributePattern(s.name())).append(")|");
            }
        }
        sb.delete(sb.length() - 1, sb.length());
        sb.append("))?");
        return sb.toString();
    }

    private String getVarNameAttributePattern(String name) {
        return name +
                "\\.VARNAME[\\s]?=[\\s]?" +
                "\"[A-Z][A-Z0-9]*\"";
    }

    private String getValueAttributePattern(String name) {
        return name +
                "\\.VALUE[\\s]?=[\\s]?" +
                "[0-9]+";
    }

    private String getStmtAttributePattern(String name) {
        return name +
                "\\.STMT#[\\s]?=[\\s]?" +
                "[0-9]+";
    }

    private String getProcNameAttributePattern(String name) {
        return name + "\\.PROCNAME[\\s]?=[\\s]?\"[A-Z][A-Z0-9]*\"";
    }
}