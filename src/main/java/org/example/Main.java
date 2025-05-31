package org.example;

import org.example.model.Lexer;
import org.example.model.Parser;
import org.example.model.ast.TNode;
import org.example.model.queryProcessor.DesignExtractor;
import org.example.model.queryProcessor.PKB;
import org.example.model.queryProcessor.QueryEvaluator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    public static void defaultRun(String[] args) {
        if (args.length < 1) {
            System.out.println("#Brak pliku źródłowego");
            return;
        }

        try {
            String program = Files.readString(Paths.get(args[0]));
            Lexer lex = new Lexer(program);
            Parser par = new Parser(lex.convertToTokens());
            TNode ast = par.parseProgram();

            PKB pkb = new PKB();
            new DesignExtractor(pkb).extract(ast);

            QueryEvaluator evaluator = new QueryEvaluator(pkb);

            System.out.println("Ready");

            Scanner in = new Scanner(System.in, "ibm852");
            while (true) {
                if (!in.hasNextLine()) break;
                String declarations = in.nextLine();
                if (!in.hasNextLine()) break;
                String query = in.nextLine();

                String result = evaluator.evaluateQuery(declarations + " " + query);
                System.out.println(result);
            }
        } catch (Exception e) {
            System.out.println("#" + e.getMessage());
        }
    }

    public static void testRun(String[] args) {

        String programFilePath = "./simple/simple_sources/source2.txt";
        String[] queryFilePaths = {"./simple/tests/test_zzz_source2.txt"};

        for (String qFile : queryFilePaths) {
            System.out.println("\n🧪 Running tests for:");
            System.out.println("Program : " + programFilePath);
            System.out.println("Queries : " + qFile + "\n");
            runTests(programFilePath, qFile);
        }
    }

    private static void runTests(String programFile, String queryFile) {
        try {
            String program = Files.readString(Paths.get(programFile));
            Lexer lex = new Lexer(program);
            Parser par = new Parser(lex.convertToTokens());
            TNode ast = par.parseProgram();

            PKB pkb = new PKB();
            new DesignExtractor(pkb).extract(ast);

            QueryEvaluator evaluator = new QueryEvaluator(pkb);

            List<String> lines = Files.readAllLines(Paths.get(queryFile));
            int passed = 0, total = 0;

            for (int i = 0; i + 2 < lines.size(); i += 3) {

                String decl = lines.get(i).trim();
                String query = lines.get(i + 1).trim();
                String expected = lines.get(i + 2).trim();
                total++;

                String raw = evaluator.evaluateQuery(decl + " " + query).trim();

                Set<String> actualSet = raw.equalsIgnoreCase("none") || raw.isBlank()
                        ? Set.of()
                        : Arrays.stream(raw.split("\\s*,\\s*"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(TreeSet::new));

                Set<String> expectedSet = expected.equalsIgnoreCase("none")
                        ? Set.of()
                        : Arrays.stream(expected.split("\\s*,\\s*"))
                        .map(String::trim)
                        .collect(Collectors.toCollection(TreeSet::new));

                if (actualSet.equals(expectedSet)) {
                    System.out.println("✅ Test " + total + " passed");
                    passed++;
                } else {
                    System.out.println("❌ Test " + total + " failed");
                    System.out.println("Query:     " + decl + " " + query);
                    System.out.println("Expected:  " + expectedSet);
                    System.out.println("Got:       " + actualSet);
                }
            }

            System.out.println("\n📊 Passed " + passed + " / " + total + " tests\n");

        } catch (Exception e) {
            System.out.println("❌ Błąd testowania: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        testRun(args);
        //defaultRun(args);
    }
}
