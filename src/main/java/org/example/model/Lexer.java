package org.example.model;

import org.example.model.enums.TokenType;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String input;
    private final int length;
    private int pos = 0;

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
                String w = word.toString();
                switch (w) {
                    case "procedure":
                        tokens.add(new Token(TokenType.PROCEDURE, w));
                        break;
                    case "while":
                        tokens.add(new Token(TokenType.WHILE, w));
                        break;
                    case "if":
                        tokens.add(new Token(TokenType.IF, w));
                        break;
                    case "then":
                        tokens.add(new Token(TokenType.THEN, w));
                        break;
                    case "else":
                        tokens.add(new Token(TokenType.ELSE, w));
                        break;
                    case "call":
                        tokens.add(new Token(TokenType.CALL, w));
                        break;
                    default:
                        tokens.add(new Token(TokenType.NAME, w));
                        break;
                }
            } else if (Character.isDigit(currentChar)) {
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
                    case '-':
                        tokens.add(new Token(TokenType.MINUS, "-"));
                        break;
                    case '*':
                        tokens.add(new Token(TokenType.TIMES, "*"));
                        break;
                    case '{':
                        tokens.add(new Token(TokenType.LBRACE, "{"));
                        break;
                    case '}':
                        tokens.add(new Token(TokenType.RBRACE, "}"));
                        break;
                    case '(':
                        tokens.add(new Token(TokenType.LPAREN, "("));
                        break;
                    case ')':
                        tokens.add(new Token(TokenType.RPAREN, ")"));
                        break;
                    case ';':
                        tokens.add(new Token(TokenType.SEMICOLON, ";"));
                        break;
                    default:
                        throw new RuntimeException("Unexpected character: " + currentChar);
                }

                pos++;
            }
        }

        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }
}
