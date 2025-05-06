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
import java.util.*;

public class Main {
    public static void main(String[] args) {
        String programFilePath = "./simple/source7.txt";
        String[] queryFilesPaths = {"./simple/test7.txt"};

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

            System.out.println("\n--- PKB: Uses by stmt ---");
            for (int stmt : pkb.getAllStmts()) {
                System.out.println("stmt " + stmt + " uses: " + pkb.getUsedByStmt(stmt));
            }

            System.out.println("\n--- PKB: Uses by proc ---");
            for (String proc : pkb.getAllProcedures()) {
                System.out.println("proc " + proc + " uses: " + pkb.getUsedByProc(proc));
            }

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
}
