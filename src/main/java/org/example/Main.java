package org.example;

import org.example.model.Lexer;
import org.example.model.Parser;
import org.example.model.Token;
import org.example.model.ast.TNode;
import org.example.model.queryProcessor.DesignExtractor;
import org.example.model.queryProcessor.PKB;
import org.example.model.queryProcessor.QueryEvaluator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("#Brak pliku źródłowego");
            return;
        }
        try {
            String content = new String(Files.readAllBytes(Paths.get(args[0])));
            Lexer lexer = new Lexer(content);
            List<Token> tokens = lexer.convertToTokens();
            Parser parser = new Parser(tokens);
            TNode ast = parser.parseProgram();
            PKB pkb = new PKB();
            DesignExtractor designExtractor = new DesignExtractor(pkb);
            designExtractor.extract(ast);
            QueryEvaluator queryEvaluator = new QueryEvaluator(pkb);
            System.out.println("Ready");
            Scanner in = new Scanner(System.in, "ibm852");

    /*      String test = "Select s1 such that Parent* (s1, 10)";
            String synonyms = "stmt s1, s2; assign a; while w; variable v;";
            System.out.println(test);
            System.out.println(queryEvaluator.evaluateQuery(synonyms + test));*/

            while (true) {
                if (!in.hasNextLine()) break;
                String declarations = in.nextLine();
                if (!in.hasNextLine()) break;
                String query = in.nextLine();
                Set<String> resultSet = queryEvaluator.evaluateQuery(declarations + query);
                String result = String.join(", ", resultSet);
                System.out.println(result);
            }
        } catch (Exception e) {
            System.out.println("#" + e.getMessage());
        }
    }
}
