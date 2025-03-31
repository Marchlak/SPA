package org.example.model.queryProcessor;

record Synonym(SynonymType type, String name) {
    @Override
    public String toString() {
        return type.getType() + " " + name;
    }
}
