package org.example;

import org.example.model.Lexer;
import org.example.model.Token;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        String input = ""; // jakies wyrazonko
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.tokenize();
        System.out.println("abcd");
    }
}