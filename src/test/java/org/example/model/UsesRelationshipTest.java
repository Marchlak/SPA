package org.example.model;

import org.example.model.enums.EntityType;
import org.example.model.queryProcessor.PKB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class UsesRelationshipTest {

    private PKB pkb;

    @BeforeEach
    public void setUp() {
        pkb = new PKB();

        pkb.addProcedure("main");
        pkb.setUsesProc("main", "x");
        pkb.setUsesProc("main", "y");

        pkb.addStmt(1, EntityType.ASSIGN);
        pkb.setUsesStmt(1, "x");
        pkb.setUsesStmt(1, "y");

        pkb.addStmt(2, EntityType.WHILE);
        pkb.setUsesStmt(2, "cond");

        pkb.addStmt(3, EntityType.CALL);
        pkb.setUsesStmt(3, "a");

        pkb.addProcedure("foo");
        pkb.setUsesProc("foo", "a");

        pkb.setCalls("main", "foo");
    }

    @Test
    public void test_Uses_knownStmt_variableSynonym() {
        Set<String> result = pkb.getUsedByStmt(1);
        assertTrue(result.contains("x"));
        assertTrue(result.contains("y"));
    }

    @Test
    public void test_Uses_stmtSynonym_knownVariable() {
        String variable = "x";
        boolean found = pkb.getAllStmts().stream()
                .anyMatch(stmt -> pkb.getUsedByStmt(stmt).contains(variable));
        assertTrue(found);
    }

    @Test
    public void test_Uses_knownProc_variableSynonym() {
        Set<String> result = pkb.getUsedByProc("main");
        assertTrue(result.contains("x"));
        assertTrue(result.contains("y"));
    }

    @Test
    public void test_Uses_procSynonym_variableSynonym() {
        boolean exists = pkb.getAllProcedures().stream()
                .anyMatch(proc -> !pkb.getUsedByProc(proc).isEmpty());
        assertTrue(exists);
    }

    @Test
    public void test_Uses_procSynonym_knownVariable() {
        String variable = "x";
        boolean found = pkb.getAllProcedures().stream()
                .anyMatch(proc -> pkb.getUsedByProc(proc).contains(variable));
        assertTrue(found);
    }

    @Test
    public void test_Uses_stmtSynonym_variableSynonym() {
        boolean exists = pkb.getAllStmts().stream()
                .anyMatch(stmt -> !pkb.getUsedByStmt(stmt).isEmpty());
        assertTrue(exists);
    }

    @Test
    public void test_callStatementUsesVariableFromCalledProc() {
        Set<String> callUses = pkb.getUsedByStmt(3);
        assertTrue(callUses.contains("a"));

        Set<String> fooUses = pkb.getUsedByProc("foo");
        assertTrue(fooUses.contains("a"));
    }
}



