package org.example;

import org.example.model.Lexer;
import org.example.model.Parser;
import org.example.model.Token;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        String input = "program MyProgram { procedure Circle { t = 1; a = t + 10; d = t * a + 2; call Triangle; b = t + a; call Hexagon; b = t + a; if t then { k = a - d; while c { d = d + t; c = d + 1; } a = d + t; } else { a = d + t; call Hexagon; c = c - 1; } call Rectangle; } procedure Rectangle { while c { t = d + 3 * a + c; call Triangle; c = c + 20; } d = t; } procedure Triangle { while d { if t then { d = t + 2; } else { a = t * a + d + k * b; } } c = t + k + d; } procedure Hexagon { t = a + t; } }";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        try {
            System.out.println(parser.parseProgram().toString(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}