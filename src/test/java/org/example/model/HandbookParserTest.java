package org.example.model;

import org.example.model.ast.TNode;
import org.example.model.enums.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HandbookParserTest {

    @Test
    void testParseAssignComplexExpression() {
        String input = "x = 1 + 2 * 3 - 4;";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode assignNode = parser.parseStmt();
        assertEquals(EntityType.ASSIGN, assignNode.getType(), "Expected node type: ASSIGN");

        TNode varNode = assignNode.getFirstChild();
        assertNotNull(varNode, "Missing variable node");
        assertEquals(EntityType.VARIABLE, varNode.getType());
        assertEquals("x", varNode.getAttr());

        TNode exprRoot = varNode.getRightSibling();
        assertNotNull(exprRoot, "Missing expression tree");
        assertEquals(EntityType.MINUS, exprRoot.getType(), "Expected expression type: MINUS");
    }

    @Test
    void testParseCall() {
        String input = "call SomeProc;";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode callNode = parser.parseStmt();
        assertEquals(EntityType.CALL, callNode.getType(), "Expected node type: CALL");

        TNode procNameNode = callNode.getFirstChild();
        assertNotNull(procNameNode, "Missing procedure name node in CALL");
        assertEquals(EntityType.VARIABLE, procNameNode.getType());
        assertEquals("SomeProc", procNameNode.getAttr());
    }

    @Test
    void testParseIf() {
        String input = "if x then { y = 2; } else { z = 3; }";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode ifNode = parser.parseStmt();
        assertEquals(EntityType.IF, ifNode.getType(), "Expected node type: IF");

        TNode condNode = ifNode.getFirstChild();
        assertNotNull(condNode, "Missing condition node in IF");
        assertEquals(EntityType.VARIABLE, condNode.getType());
        assertEquals("x", condNode.getAttr());

        TNode thenStmtList = condNode.getRightSibling();
        assertNotNull(thenStmtList, "Missing 'then' block");
        assertEquals(EntityType.STMTLIST, thenStmtList.getType());
        TNode thenAssign = thenStmtList.getFirstChild();
        assertNotNull(thenAssign, "Missing statement in 'then' block");
        assertEquals(EntityType.ASSIGN, thenAssign.getType());

        TNode elseStmtList = thenStmtList.getRightSibling();
        assertNotNull(elseStmtList, "Missing 'else' block");
        assertEquals(EntityType.STMTLIST, elseStmtList.getType());
        TNode elseAssign = elseStmtList.getFirstChild();
        assertNotNull(elseAssign, "Missing statement in 'else' block");
        assertEquals(EntityType.ASSIGN, elseAssign.getType());
    }

    @Test
    void testParseWhile() {
        String input = "while a { x = 5; }";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode whileNode = parser.parseStmt();
        assertEquals(EntityType.WHILE, whileNode.getType(), "Expected node type: WHILE");

        TNode condNode = whileNode.getFirstChild();
        assertNotNull(condNode, "Missing condition in WHILE");
        assertEquals(EntityType.VARIABLE, condNode.getType());
        assertEquals("a", condNode.getAttr());

        TNode stmtList = condNode.getRightSibling();
        assertNotNull(stmtList, "Missing statement list in WHILE");
        assertEquals(EntityType.STMTLIST, stmtList.getType());
        TNode innerAssign = stmtList.getFirstChild();
        assertNotNull(innerAssign, "Missing statement inside WHILE");
        assertEquals(EntityType.ASSIGN, innerAssign.getType());
    }

    @Test
    void testParseStmtListMultipleStatements() {
        String input = "x = 1; y = 2; z = 3;}";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode stmtList = parser.parseStmtList();
        assertEquals(EntityType.STMTLIST, stmtList.getType(), "Expected node type: STMTLIST");

        int count = 0;
        for (TNode stmt = stmtList.getFirstChild(); stmt != null; stmt = stmt.getRightSibling()) {
            count++;
        }
        assertEquals(3, count, "Expected 3 statements in list");
    }

    @Test
    void testParseFullProgram() {
        String input = "program MyProg { procedure A { x = 1; } procedure B { call A; } }";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode programNode = parser.parseProgram();
        assertEquals(EntityType.PROGRAM, programNode.getType(), "Expected node type: PROGRAM");
        assertEquals("MyProg", programNode.getAttr(), "Program name should be 'MyProg'");

        TNode procList = programNode.getFirstChild();
        assertNotNull(procList, "Missing procedure list in program");
        assertEquals(EntityType.PROCLIST, procList.getType());

        int procCount = 0;
        for (TNode proc = procList.getFirstChild(); proc != null; proc = proc.getRightSibling()) {
            procCount++;
        }
        assertEquals(2, procCount, "Expected 2 procedures");
    }

    @Test
    void testEdgeCaseDeeplyNestedStructures() {
        String input = "procedure Crazy { if x then { while y { if z then { a = 1; } else { b = 2; } } } else { c = 3; } }";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode proc = parser.parseProcedure();
        assertEquals("Crazy", proc.getAttr(), "Procedure name should be 'Crazy'");
    }

    @Test
    void testEdgeCaseMissingClosingBrace() {
        String input = "procedure Incomplete { x = 1;";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        assertThrows(RuntimeException.class, parser::parseProcedure);
    }
}
