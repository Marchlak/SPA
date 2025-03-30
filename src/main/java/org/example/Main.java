package org.example;

import org.example.model.Lexer;
import org.example.model.Parser;
import org.example.model.Token;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        String input = "procedure Second { x = 0; i = 5; while i { x = x + 2*y; call Third; i = i - 1; } if x then { x = x+1; } else { z = 1; } z = z + x + i; y = z + 2; x = x * y + z; }";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        try {
            System.out.println(parser.parseProcedure().toString(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}