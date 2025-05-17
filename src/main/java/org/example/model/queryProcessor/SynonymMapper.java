package org.example.model.queryProcessor;

class SynonymMapper {
    private SynonymMapper() {}
    public static SynonymType toSynonymType(String string) {
        return switch (string) {
            case "STMT" -> SynonymType.STMT;
            case "ASSIGN" -> SynonymType.ASSIGN;
            case "WHILE" -> SynonymType.WHILE;
            case "IF" -> SynonymType.IF;
            case "VARIABLE" -> SynonymType.VARIABLE;
            case "CONSTANT" -> SynonymType.CONSTANT;
            case "PROG_LINE" -> SynonymType.PROG_LINE;
            case "PROCEDURE" -> SynonymType.PROCEDURE;
            default -> throw new IllegalStateException("Unexpected value: " + string);
        };
    }
}
