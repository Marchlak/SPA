package org.example.model.queryProcessor;

class Synonym {
    private SynonymType type;
    private String name;

    public Synonym(SynonymType type, String name) {
        this.type = type;
        this.name = name;
    }

    public SynonymType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return type.getType() + " " + name;
    }
}
