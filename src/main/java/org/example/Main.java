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

            // String test = "Select s1 such that Parent* (s1, 10)";
            // String synonyms = "stmt s1, s2; assign a; while w; variable v;";
            // System.out.println(test);
            // System.out.println(queryEvaluator.evaluateQuery(synonyms + test));

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

    private static void lololo() {
        String input = "program MyProgram { procedure Circle { t = 1; a = t + 10; d = t * a + 2; call Triangle; b = t + a; call Hexagon; b = t + a; if t then { k = a - d; while c { d = d + t; c = d + 1; } a = d + t; } else { a = d + t; call Hexagon; c = c - 1; } call Rectangle; } procedure Rectangle { while c { t = d + 3 * a + c; call Triangle; c = c + 20; } d = t; } procedure Triangle { while d { if t then { d = t + 2; } else { a = t * a + d + k * b; } } c = t + k + d; } procedure Hexagon { t = a + t; } }";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);
        TNode ast = parser.parseProgram();
        PKB pkb = new PKB();
        DesignExtractor designExtractor = new DesignExtractor(pkb);
        designExtractor.extract(ast);
        QueryEvaluator queryEvaluator = new QueryEvaluator(pkb);
        String declarations = "stmt a;";
        String query = "select a such that Follows* (a, 4) with a.stmt# = 1"; //   Parent (8, a) and
        System.out.println(queryEvaluator.evaluateQuery(declarations + query));

    }
}
//package org.example;
//
//import org.example.model.Lexer;
//import org.example.model.Parser;
//import org.example.model.Token;
//import org.example.model.ast.TNode;
//import org.example.model.queryProcessor.DesignExtractor;
//import org.example.model.queryProcessor.PKB;
//import org.example.model.queryProcessor.QueryEvaluator;
//
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.*;
//
//public class Main {
//    public static void main(String[] args) {
//        String programFilePath = "./simple/source7.txt";
//        String[] queryFilesPaths = {"./simple/test7.txt"};
//
//        for (String queryFile : queryFilesPaths) {
//            System.out.println("\n🧪 Running tests from: " + queryFile);
//            runTests(programFilePath, queryFile);
//        }
//    }
//
//    private static void runTests(String programFile, String queryFile) {
//        try {
//            String input = Files.readString(Paths.get(programFile));
//            Lexer lexer = new Lexer(input);
//            List<Token> tokens = lexer.convertToTokens();
//            Parser parser = new Parser(tokens);
//            TNode ast = parser.parseProgram();
//
//            PKB pkb = new PKB();
//            DesignExtractor extractor = new DesignExtractor(pkb);
//            extractor.extract(ast);
//
//            System.out.println("\n--- PKB: Uses by stmt ---");
//            for (int stmt : pkb.getAllStmts()) {
//                System.out.println("stmt " + stmt + " uses: " + pkb.getUsedByStmt(stmt));
//            }
//
//            System.out.println("\n--- PKB: Uses by proc ---");
//            for (String proc : pkb.getAllProcedures()) {
//                System.out.println("proc " + proc + " uses: " + pkb.getUsedByProc(proc));
//            }
//
//            QueryEvaluator evaluator = new QueryEvaluator(pkb);
//
//            List<String> lines = Files.readAllLines(Paths.get(queryFile));
//            int passed = 0, total = 0;
//
//            for (int i = 0; i + 2 < lines.size(); i += 3) {
//                String decl = lines.get(i).trim();
//                String query = lines.get(i + 1).trim();
//                String expected = lines.get(i + 2).trim();
//                total++;
//
//                Set<String> rawResult = evaluator.evaluateQuery(decl + " " + query);
//
//                Set<String> actualSet = (rawResult.size() == 1 && rawResult.contains("none"))
//                        ? Set.of()
//                        : rawResult;
//
//                Set<String> expectedSet = expected.equalsIgnoreCase("none")
//                        ? Set.of()
//                        : new TreeSet<>(List.of(expected.split("\\s*,\\s*")));
//
//                Set<String> actualSorted = new TreeSet<>(actualSet);
//
//                if (actualSorted.equals(expectedSet)) {
//                    System.out.println("✅ Test " + total + " passed");
//                    passed++;
//                } else {
//                    System.out.println("❌ Test " + total + " failed");
//                    System.out.println("Query:     " + decl + " " + query);
//                    System.out.println("Expected:  " + expectedSet);
//                    System.out.println("Got:       " + actualSorted);
//                }
//            }
//
//            System.out.println("\n📊 Passed " + passed + " out of " + total + " tests in " + queryFile);
//
//        } catch (Exception e) {
//            System.out.println("❌ Błąd testowania: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//}
