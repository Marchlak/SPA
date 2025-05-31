package org.example.model.queryProcessor;

enum RelationshipType {
    FOLLOWS("FOLLOWS"),
    FOLLOWS_STAR("FOLLOWS\\*"),
    CALLS("CALLS"),
    CALLS_STAR("CALLS\\*"),
    PARENT("PARENT"),
    PARENT_STAR("PARENT\\*"),
    MODIFIES("MODIFIES"),
    USES("USES"),
    NEXT("NEXT"),
    NEXT_STAR("NEXT\\*"),
    AFFECTS("AFFECTS"),
    AFFECTS_STAR("AFFECTS\\*");

    private final String type;

    RelationshipType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
