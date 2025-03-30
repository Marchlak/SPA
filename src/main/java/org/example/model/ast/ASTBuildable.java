package org.example.model.ast;

public interface ASTBuildable {
    TNode CreateTNode(EntityType entityType);
    void SetRoot(TNode astNode);
    void SetAttribute(TNode tnode, String attribute);
    void setFirstChild (TNode parent, TNode child);
    void setRightSibling (TNode left, TNode right);
}
