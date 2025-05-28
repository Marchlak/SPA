package org.example.model.queryProcessor;

public class Relationship {
    private final RelationshipType type;
    private final String rawArgs; // np. "s1, s2"

    public Relationship(RelationshipType type, String rawArgs) {
        this.type = type;
        this.rawArgs = rawArgs;
    }

    public RelationshipType getType() {
        return type;
    }

    public String getFirstArg() {
        String[] parts = rawArgs.split(",");
        return parts[0].trim();
    }

    public String getSecondArg() {
        String[] parts = rawArgs.split(",");
        return parts[1].trim();
    }
}
