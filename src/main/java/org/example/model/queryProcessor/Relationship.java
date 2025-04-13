package org.example.model.queryProcessor;

class Relationship {
    private RelationshipType type;
    private String firstArg;
    private String secondArg;

    Relationship(RelationshipType type, String args) {
        try {
            this.type = type;
            this.firstArg = args.split(",")[0].trim();
            this.secondArg = args.split(",")[1].trim();
        } catch (Exception ignored) {
        }
    }

    public RelationshipType getType() {
        return type;
    }

    public void setType(RelationshipType type) {
        this.type = type;
    }

    public String getFirstArg() {
        return firstArg;
    }

    public void setFirstArg(String firstArg) {
        this.firstArg = firstArg;
    }

    public String getSecondArg() {
        return secondArg;
    }

    public void setSecondArg(String secondArg) {
        this.secondArg = secondArg;
    }
    
}
