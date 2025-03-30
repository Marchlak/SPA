package org.example.model.ast;

import java.util.ArrayList;
import java.util.List;

public class TNode {
    private final EntityType entityType;
    String attribute;
    TNode parent;
    TNode rightSibling;
    final List<TNode> children = new ArrayList<>();

    public TNode(EntityType entityType) {
        this.entityType = entityType;
    }

    public TNode GetParent() {
        return parent;
    }

    public void SetParent(TNode parent) {
        this.parent = parent;
    }

    public TNode GetRightSibling() {
        return rightSibling;
    }

    public void SetRightSibling(TNode rightSibling) {
        this.rightSibling = rightSibling;
    }

    public void SetNthChild(int nth, TNode child) {
        children.set(nth, child);
    }

    public int GetChildrenIndex(TNode child) {
        return children.indexOf(child);
    }

    public void SetAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String GetAttribute() {
        return attribute;
    }
}
