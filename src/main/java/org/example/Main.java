package org.example;

import org.example.model.Lexer;
import org.example.model.Parser;
import org.example.model.Token;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        String input = "procedure main { }"; // jakies wyrazonko
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        try {
            parser.parseProcedure();
        } catch (Exception e){
            e.printStackTrace();
        }

        System.out.println("abcd");
    }
}