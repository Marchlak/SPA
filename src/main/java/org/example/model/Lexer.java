package org.example.model;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private String input;
    private Integer pos = 0;
    private Integer length;

    public Lexer(String input) {
        this.input = input;
        this.length = input.length();
    }

    public List<Token> convertToTokens() {
        List<Token> tokens = new ArrayList<>();

        while (pos < length) {
            char currentChar = input.charAt(pos);

            if (Character.isWhitespace(currentChar)) {
                pos++;
            } else if (Character.isLetter(currentChar)) {
                StringBuilder word = new StringBuilder();

                while (pos < length && Character.isLetterOrDigit(input.charAt(pos))) {
                    word.append(input.charAt(pos));
                    pos++;
                }

                if (word.toString().equals("procedure")) {
                    tokens.add(new Token(TokenType.PROCEDURE, word.toString()));
                } else if (word.toString().equals("while")) {
                    tokens.add(new Token(TokenType.WHILE, word.toString()));
                } else {
                    tokens.add(new Token(TokenType.NAME, word.toString()));
                }
            } else if (Character.isDigit(currentChar)){
                StringBuilder number = new StringBuilder();

                while (pos < length && Character.isDigit(input.charAt(pos))) {
                    number.append(input.charAt(pos));
                    pos++;
                }

                tokens.add(new Token(TokenType.INTEGER, number.toString()));
            } else {
                switch (currentChar) {
                    case '=':
                        tokens.add(new Token(TokenType.EQUAL, "="));
                        break;
                    case '+':
                        tokens.add(new Token(TokenType.PLUS, "+"));
                        break;
                    case '{':
                        tokens.add(new Token(TokenType.LBRACE, "{"));
                        break;
                    case '}':
                        tokens.add(new Token(TokenType.RBRACE, "}"));
                        break;
                    case ';':
                        tokens.add(new Token(TokenType.SEMICOLON, ";"));
                        break;
                    default:
                        throw new RuntimeException("Unexpected character");
                }

                pos++;
            }
        }
        tokens.add(new Token(TokenType.EOF,""));

        return tokens;
    }
}
