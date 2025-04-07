package org.example;

import org.example.model.Lexer;
import org.example.model.Parser;
import org.example.model.Token;
import org.example.model.ast.TNode;
import org.example.model.queryProcessor.DesignExtractor;
import org.example.model.queryProcessor.PKB;
import org.example.model.queryProcessor.QueryEvaluator;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        String input = "program MyProgram { procedure Circle { t = 1; a = t + 10; d = t * a + 2; call Triangle; b = t + a; call Hexagon; b = t + a; if t then { k = a - d; while c { d = d + t; c = d + 1; } a = d + t; } else { a = d + t; call Hexagon; c = c - 1; } call Rectangle; } procedure Rectangle { while c { t = d + 3 * a + c; call Triangle; c = c + 20; } d = t; } procedure Triangle { while d { if t then { d = t + 2; } else { a = t * a + d + k * b; } } c = t + k + d; } procedure Hexagon { t = a + t; } }";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);
        TNode ast = parser.parseProgram();
        PKB pkb = new PKB();
        DesignExtractor designExtractor = new DesignExtractor(pkb);
        designExtractor.extract(ast);
        QueryEvaluator queryEvaluator = new QueryEvaluator(pkb);
        try {
            System.out.println("Parent(10) -> " + pkb.getParent(10));
            System.out.println("Parent*(11) -> " + pkb.getParentStar(11));
            System.out.println("ParentBy(8) -> " + pkb.getParentedBy(8));

            System.out.println("\n");

            System.out.println("Follows(1) -> " + pkb.getFollows(1));
            System.out.println("FollowedBy(8) -> " + pkb.getFollowedBy(8));
            System.out.println("Follows*(8) -> " + pkb.getFollowedByStar(8));

            System.out.println("\n");

            System.out.println("Modified by stmt 3 -> " + pkb.getModifiedByStmt(3));
            System.out.println("Modified by stmt 7 -> " + pkb.getModifiedByStmt(7));
            System.out.println("Modified by proc Triangle -> " + pkb.getModifiedByProc("Triangle"));

            System.out.println("\n");
            System.out.println("Used by stmt 2 (a = t + 10) -> " + pkb.getUsedByStmt(2));
            System.out.println("Used by stmt 3 (d = t * a + 2) -> " + pkb.getUsedByStmt(4));

            System.out.println("Used by proc Triangle " + pkb.getUsedByProc("Triangle"));

            System.out.println("\n");
            String synonyms = "stmt s1, s2; assign a; while w; variable v;";
            System.out.println("Select s1 such that Follows (s1, 10)");
            System.out.println(queryEvaluator.evaluateQuery(synonyms + "Select s1 such that Uses (23, v)"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}