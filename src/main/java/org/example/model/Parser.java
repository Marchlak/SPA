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

        return procNode;
    }

    public TNode parseStmtList() {
        TokenType type = getToken().getType();

        if (type != TokenType.NAME && type != TokenType.WHILE) {
            throw new RuntimeException("stmtList must contain at least one instruction (NAME lub WHILE)");
        }

        TNode stmtListNode = new TNode(EntityType.STMTLIST);

        TNode firstStmt = parseStmt();
        stmtListNode.setFirstChild(firstStmt);

        TNode current = firstStmt;

        while (getToken().getType() == TokenType.NAME || getToken().getType() == TokenType.WHILE) {
            TNode nextStmt = parseStmt();
            current.setRightSibling(nextStmt);
            current = nextStmt;
        }

        return stmtListNode;
    }


    public TNode parseStmt() {
        Token token = getToken();

        return switch (token.getType()) {
            case WHILE -> parseWhile();
            case NAME -> parseAssign();
            default -> throw new RuntimeException("Unexpected token in statement: " + token);
        };
    }


    public TNode parseWhile() {
        TNode whileNode = new TNode(EntityType.WHILE);
        checkToken(TokenType.WHILE);
        Token varToken = checkToken(TokenType.NAME);
        TNode condNode = new TNode(EntityType.VARIABLE);
        condNode.setAttr(varToken.getValue());
        whileNode.setFirstChild(condNode);
        checkToken(TokenType.LBRACE);
        TNode stmtList = parseStmtList();
        condNode.setRightSibling(stmtList);
        checkToken(TokenType.RBRACE);
        return whileNode;
    }


    public TNode parseAssign() {
        Token varToken = checkToken(TokenType.NAME);
        checkToken(TokenType.EQUAL);
        TNode exprNode = parseExpr();
        checkToken(TokenType.SEMICOLON);

        TNode assignNode = new TNode(EntityType.ASSIGN);
        TNode varNode = new TNode(EntityType.VARIABLE);
        varNode.setAttr(varToken.getValue());

        assignNode.setFirstChild(varNode);
        varNode.setRightSibling(exprNode);

        return assignNode;
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

    public TNode parseProgram() {
        checkToken(TokenType.PROGRAM);
        Token nameToken = checkToken(TokenType.NAME);

        TNode programNode = new TNode(EntityType.PROGRAM);
        programNode.setAttr(nameToken.getValue());

        checkToken(TokenType.LBRACE);

        TNode procListNode = new TNode(EntityType.PROCLIST);
        programNode.setFirstChild(procListNode);

        while (getToken().getType() == TokenType.PROCEDURE) {
            TNode procedureNode = parseProcedure();

            if (procListNode.getFirstChild() == null) {
                procListNode.setFirstChild(procedureNode);
            } else {
                TNode current = procListNode.getFirstChild();
                while (current.getRightSibling() != null) {
                    current = current.getRightSibling();
                }
                current.setRightSibling(procedureNode);
            }
        }

        checkToken(TokenType.RBRACE);
        checkToken(TokenType.EOF);

        return programNode;
    }

}
