package org.example.model.ast;

public interface ASTBuildable {
    TNode CreateTNode(EntityType et);
    void SetRoot(TNode astNode);
    void SetAttribute(TNode t, Attribute a);
    void setFirstChild (TNode parent, TNode child);
    void setRightSibling (TNode left, TNode right);
}
