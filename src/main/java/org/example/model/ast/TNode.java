package org.example.model.ast;

import org.example.model.enums.EntityType;

public class TNode {
    private final EntityType type;

    private String attr;
    private TNode firstChild;
    private TNode rightSibling;
    private TNode parent;

    public TNode(EntityType type) {
        this.type = type;
    }

    public EntityType getType() {
        return type;
    }

    public String getAttr() {
        return attr;
    }

    public void setAttr(String attr) {
        this.attr = attr;
    }

    public TNode getFirstChild() {
        return firstChild;
    }

    public void setFirstChild(TNode child) {
        this.firstChild = child;
        if (child != null) {
            child.setParent(this);
        }
    }

    public TNode getRightSibling() {
        return rightSibling;
    }

    public void setRightSibling(TNode sibling) {
        this.rightSibling = sibling;
        if (sibling != null) {
            sibling.setParent(this.parent);
        }
    }

    public TNode getParent() {
        return parent;
    }

    public void setParent(TNode parent) {
        this.parent = parent;
    }

    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ".repeat(Math.max(0, indent)));
        sb.append(type);
        if (attr != null) sb.append(":").append(attr);
        sb.append("\n");
        if (firstChild != null) sb.append(firstChild.toString(indent + 1));
        if (rightSibling != null) sb.append(rightSibling.toString(indent));
        return sb.toString();
    }
}
