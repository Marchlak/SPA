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
import java.util.TreeSet;

public class Main {

    public static void defaultRun(String[] args) {
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

    public static void testRun(String[] args) {
        String programFilePath = "./simple/simple_sources/source1.txt";
        String[] queryFilesPaths = {"./simple/tests/test_calls_source1.txt"};

        for (String queryFile : queryFilesPaths) {
            System.out.println("\n🧪 Running tests from: " + queryFile);
            runTests(programFilePath, queryFile);
        }
    }

    private static void runTests(String programFile, String queryFile) {
        try {
            String input = Files.readString(Paths.get(programFile));
            Lexer lexer = new Lexer(input);
            List<Token> tokens = lexer.convertToTokens();
            Parser parser = new Parser(tokens);
            TNode ast = parser.parseProgram();

            PKB pkb = new PKB();
            DesignExtractor extractor = new DesignExtractor(pkb);
            extractor.extract(ast);

//            System.out.println("\n--- PKB: Uses by stmt ---");
//            for (int stmt : pkb.getAllStmts()) {
//                System.out.println("stmt " + stmt + " uses: " + pkb.getUsedByStmt(stmt));
//            }
//
//            System.out.println("\n--- PKB: Uses by proc ---");
//            for (String proc : pkb.getAllProcedures()) {
//                System.out.println("proc " + proc + " uses: " + pkb.getUsedByProc(proc));
//            }

            QueryEvaluator evaluator = new QueryEvaluator(pkb);

            List<String> lines = Files.readAllLines(Paths.get(queryFile));
            int passed = 0, total = 0;

            for (int i = 0; i + 2 < lines.size(); i += 3) {
                String decl = lines.get(i).trim();
                String query = lines.get(i + 1).trim();
                String expected = lines.get(i + 2).trim();
                total++;

                Set<String> rawResult = evaluator.evaluateQuery(decl + " " + query);

                Set<String> actualSet = (rawResult.size() == 1 && rawResult.contains("none"))
                        ? Set.of()
                        : rawResult;

                Set<String> expectedSet = expected.equalsIgnoreCase("none")
                        ? Set.of()
                        : new TreeSet<>(List.of(expected.split("\\s*,\\s*")));

                Set<String> actualSorted = new TreeSet<>(actualSet);

                if (actualSorted.equals(expectedSet)) {
                    System.out.println("✅ Test " + total + " passed");
                    passed++;
                } else {
                    System.out.println("❌ Test " + total + " failed");
                    System.out.println("Query:     " + decl + " " + query);
                    System.out.println("Expected:  " + expectedSet);
                    System.out.println("Got:       " + actualSorted);
                }
            }

            System.out.println("\n📊 Passed " + passed + " out of " + total + " tests in " + queryFile);

        } catch (Exception e) {
            System.out.println("❌ Błąd testowania: " + e.getMessage());
            e.printStackTrace();
        }
    }




    public static void main(String[] args) {
    //     testRun(args);
       defaultRun(args);
    }

}
