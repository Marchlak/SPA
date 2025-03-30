package org.example.model.ast;

public class AST implements ASTBuildable, ASTQueryable {
    TNode root;
    @Override
    public TNode CreateTNode(EntityType entityType) {
        TNode newNode = new TNode(entityType);
        return newNode;
    }

    @Override
    public void SetRoot(TNode astNode) {
        root = astNode;
    }

    @Override
    public void SetAttribute(TNode t, String a) {
        t.SetAttribute(a);
    }

    @Override
    public void setFirstChild(TNode parent, TNode child) {
        parent.SetNthChild(0, child);
        child.SetParent(parent);
    }

    @Override
    public void setRightSibling(TNode left, TNode right) {
        //For replace with link creating method
        TNode parent = left.GetParent();
        int indexOfLeftNode = parent.GetChildrenIndex(left);
        left.SetRightSibling(right);
        parent.SetNthChild(indexOfLeftNode + 1, right);
    }
}
