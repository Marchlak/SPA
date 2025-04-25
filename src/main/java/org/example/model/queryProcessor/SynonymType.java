package org.example.model.queryProcessor;

enum SynonymType {
    STMT("STMT"),
    ASSIGN("ASSIGN"),
    WHILE("WHILE"),
    CALL("CALL"),
    IF("IF"),
    VARIABLE("VARIABLE"),
    CONSTANT("CONSTANT"),
    PROG_LINE("PROG_LINE"),
    PROCEDURE("PROCEDURE");

    private final String type;

    SynonymType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
