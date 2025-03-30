package org.example.model;

import org.example.model.ast.TNode;
import org.example.model.enums.EntityType;
import org.example.model.enums.TokenType;

import java.util.List;

public class Parser {
    private final List<Token> tokens;

    int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Token getToken() {
        return tokens.get(pos);
    }

    private Token checkToken(TokenType type) {
        Token token = getToken();
        if (token.getType() != type) {
            throw new RuntimeException("Wrong token. Expected: " + type + ", but got: " + token.getType());
        }
        pos++;
        return token;
    }

    public TNode parseProcedure() {
        checkToken(TokenType.PROCEDURE);

        Token nameToken = checkToken(TokenType.NAME);
        TNode procNode = new TNode(EntityType.PROCEDURE);

        procNode.setAttr(nameToken.getValue());

        checkToken(TokenType.LBRACE);

        TNode stmtListNode = parseStmtList();
        procNode.setFirstChild(stmtListNode);

        checkToken(TokenType.RBRACE);
        checkToken(TokenType.EOF);

        TNode programNode = new TNode(EntityType.PROGRAM);
        programNode.setFirstChild(procNode);

        return programNode;
    }

    public TNode parseStmtList() {

        return null;
    }

    public void parseStmt() {

    }

    public void parseWhile() {

    }

    public void parseAssign() {

    }

    private TNode parseExpr() {
        TNode left = parseTerm();
        while (getToken().getType() == TokenType.PLUS || getToken().getType() == TokenType.MINUS) {
            Token op = getToken();
            pos++;
            TNode opNode = new TNode(op.getType() == TokenType.PLUS ? EntityType.PLUS : EntityType.MINUS);
            opNode.setFirstChild(left);
            TNode right = parseTerm();
            left.setRightSibling(right);
            left = opNode;
        }
        return left;
    }

    private TNode parseTerm() {
        TNode left = parseFactor();
        while (getToken().getType() == TokenType.TIMES) {
            Token op = getToken();
            pos++;
            TNode opNode = new TNode(EntityType.TIMES);
            opNode.setFirstChild(left);
            TNode right = parseFactor();
            left.setRightSibling(right);
            left = opNode;
        }
        return left;
    }

    private TNode parseFactor() {
        Token token = getToken();
        if (token.getType() == TokenType.INTEGER) {
            pos++;
            TNode constNode = new TNode(EntityType.CONSTANT);
            constNode.setAttr(token.getValue());
            return constNode;
        } else if (token.getType() == TokenType.NAME) {
            pos++;
            TNode varNode = new TNode(EntityType.VARIABLE);
            varNode.setAttr(token.getValue());
            return varNode;
        } else if (token.getType() == TokenType.LPAREN) {
            checkToken(TokenType.LPAREN);
            TNode exprNode = parseExpr();
            checkToken(TokenType.RPAREN);
            return exprNode;
        } else {
            throw new RuntimeException("Unexpected token in factor: " + token);
        }
    }
}
