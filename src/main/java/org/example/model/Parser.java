package org.example.model;

import java.util.List;

public class Parser {
    private List<Token> tokens;
    int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Token getToken() {
        return tokens.get(pos);
    }

    public Token checkToken(TokenType type) {
        Token token = getToken();

        if (token.getType() != type) {
            throw new RuntimeException("Wrong token");
        }
        pos++;

        return token;
    }

    public void parseProcedure() {
        checkToken(TokenType.PROCEDURE);
        checkToken(TokenType.NAME);
        checkToken(TokenType.LBRACE);
        // parsowanie stmtList
        checkToken(TokenType.RBRACE);
        checkToken(TokenType.EOF);
    }

    public void parseStmtList() {

    }

    public void parseStmt() {

    }

    public void parseWhile() {

    }

    public void parseAssign() {

    }

    public void parseExpr() {

    }

    public void parseFactor() {

    }
}
