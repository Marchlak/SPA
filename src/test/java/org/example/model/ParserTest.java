package org.example.model;

import org.example.model.ast.TNode;
import org.example.model.enums.EntityType;
import org.example.model.enums.TokenType;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    @Test
    void testParseAssign() {
        String input = "x = 42;";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode node = parser.parseStmt();

        assertEquals(EntityType.ASSIGN, node.getType());

        TNode variable = node.getFirstChild();
        assertNotNull(variable);
        assertEquals(EntityType.VARIABLE, variable.getType());
        assertEquals("x", variable.getAttr());

        TNode constant = variable.getRightSibling();
        assertNotNull(constant);
        assertEquals(EntityType.CONSTANT, constant.getType());
        assertEquals("42", constant.getAttr());
    }

    @Test
    void testParseWhile() {
        String input = "while i { y = 1; }";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode node = parser.parseStmt();

        assertEquals(EntityType.WHILE, node.getType());

        TNode condition = node.getFirstChild();
        assertNotNull(condition);
        assertEquals(EntityType.VARIABLE, condition.getType());
        assertEquals("i", condition.getAttr());

        TNode stmtList = condition.getRightSibling();
        assertNotNull(stmtList);
        assertEquals(EntityType.STMTLIST, stmtList.getType());

        TNode innerStmt = stmtList.getFirstChild();
        assertNotNull(innerStmt);
        assertEquals(EntityType.ASSIGN, innerStmt.getType());
    }

    @Test
    void testParseInvalidStatementThrows() {
        String input = "then";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        assertThrows(RuntimeException.class, parser::parseStmt);
    }


    @Test
    void testParseStmtListWithMultipleAssigns() {
        String input = "x = 1; y = 2; z = 3;";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode stmtList = parser.parseStmtList();

        assertEquals(EntityType.STMTLIST, stmtList.getType());

        TNode stmt1 = stmtList.getFirstChild();
        assertNotNull(stmt1);
        assertEquals(EntityType.ASSIGN, stmt1.getType());

        TNode stmt2 = stmt1.getRightSibling();
        assertNotNull(stmt2);
        assertEquals(EntityType.ASSIGN, stmt2.getType());

        TNode stmt3 = stmt2.getRightSibling();
        assertNotNull(stmt3);
        assertEquals(EntityType.ASSIGN, stmt3.getType());

        assertNull(stmt3.getRightSibling());
    }

    @Test
    public void testParseSimpleProgram() {
        String input = "program myProgram { procedure myProcedure { x = 5; } }";

        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();

        Parser parser = new Parser(tokens);

        TNode programNode = parser.parseProgram();

        assertNotNull(programNode);
        assertEquals(EntityType.PROGRAM, programNode.getType());

        assertEquals("myProgram", programNode.getAttr());

        TNode procListNode = programNode.getFirstChild();
        assertNotNull(procListNode);
        assertEquals(EntityType.PROCLIST, procListNode.getType());

        TNode procedureNode = procListNode.getFirstChild();
        assertNotNull(procedureNode);
        assertEquals(EntityType.PROCEDURE, procedureNode.getType());
        assertEquals("myProcedure", procedureNode.getAttr());
    }

    @Test
    void testParseProcedureComplex() {
        String input = "procedure Second { x = 0; i = 5; while i { x = x + 2*y; call Third; i = i - 1; } if x then { x = x+1; } else { z = 1; } z = z + x + i; y = z + 2; x = x * y + z; }";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode procNode = parser.parseProcedure();
        assertEquals(EntityType.PROCEDURE, procNode.getType());
        assertEquals("Second", procNode.getAttr());

        TNode stmtList = procNode.getFirstChild();
        assertNotNull(stmtList);
        assertEquals(EntityType.STMTLIST, stmtList.getType());

        int count = 0;
        for (TNode stmt = stmtList.getFirstChild(); stmt != null; stmt = stmt.getRightSibling()) {
            count++;
        }
        assertEquals(7, count);
    }

    @Test
    void testParseFullProgramComplex() {
        String input = "program MyProgram { procedure Circle { t = 1; a = t + 10; d = t * a + 2; call Triangle; b = t + a; call Hexagon; b = t + a; if t then { k = a - d; while c { d = d + t; c = d + 1; } a = d + t; } else { a = d + t; call Hexagon; c = c - 1; } call Rectangle; } procedure Rectangle { while c { t = d + 3 * a + c; call Triangle; c = c + 20; } d = t; } procedure Triangle { while d { if t then { d = t + 2; } else { a = t * a + d + k * b; } } c = t + k + d; } procedure Hexagon { t = a + t; } }";
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.convertToTokens();
        Parser parser = new Parser(tokens);

        TNode programNode = parser.parseProgram();
        assertNotNull(programNode);
        assertEquals(EntityType.PROGRAM, programNode.getType());
        assertEquals("MyProgram", programNode.getAttr());

        TNode procList = programNode.getFirstChild();
        assertNotNull(procList);
        assertEquals(EntityType.PROCLIST, procList.getType());

        int procCount = 0;
        TNode proc = procList.getFirstChild();
        while (proc != null) {
            procCount++;
            proc = proc.getRightSibling();
        }
        assertEquals(4, procCount);
    }

}