package org.example.model;

import org.example.model.enums.EntityType;
import org.example.model.queryProcessor.PKB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class RelationshipTest {

    private PKB pkb;

    @BeforeEach
    public void setUp() {
        pkb = new PKB();

        pkb.addProcedure("main");
        pkb.setUsesProc("main", "x");
        pkb.setUsesProc("main", "y");
        pkb.setModifiesProc("main", "x");
        pkb.setModifiesProc("main", "y");

        pkb.addStmt(1, EntityType.ASSIGN);
        pkb.setUsesStmt(1, "x");
        pkb.setUsesStmt(1, "y");
        pkb.setModifiesStmt(1, "x");
        pkb.setModifiesStmt(1, "y");

        pkb.addStmt(2, EntityType.WHILE);
        pkb.setUsesStmt(2, "cond");

        pkb.addStmt(3, EntityType.CALL);
        pkb.setUsesStmt(3, "a");
        pkb.setModifiesStmt(3, "a");

        pkb.addProcedure("foo");
        pkb.setUsesProc("foo", "a");
        pkb.setModifiesProc("foo", "a");

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

    @Test
    public void test_ModifiesRelationships() {

        assertAll("MODIFIES checks (stmt-level, proc-level, propagacja przez call)",

                () -> assertTrue(pkb.getModifiedByStmt(1).containsAll(Set.of("x", "y")),
                        "stmt 1 powinien modyfikować x oraz y"),

                () -> assertTrue(
                        pkb.getAllStmts().stream()
                                .anyMatch(s -> pkb.getModifiedByStmt(s).contains("x")),
                        "co najmniej jedna instrukcja powinna modyfikować x"),

                () -> assertTrue(
                        pkb.getAllStmts().stream()
                                .anyMatch(s -> !pkb.getModifiedByStmt(s).isEmpty()),
                        "powinna istnieć instrukcja modyfikująca jakąś zmienną"),

                () -> assertTrue(pkb.getModifiedByProc("main").containsAll(Set.of("x", "y")),
                        "procedura main powinna modyfikować x oraz y"),

                () -> assertTrue(
                        pkb.getAllProcedures().stream()
                                .anyMatch(p -> !pkb.getModifiedByProc(p).isEmpty()),
                        "powinna istnieć procedura modyfikująca co najmniej jedną zmienną"),

                () -> assertTrue(
                        pkb.getAllProcedures().stream()
                                .anyMatch(p -> pkb.getModifiedByProc(p).contains("x")),
                        "jakakolwiek procedura powinna modyfikować x"),

                () -> assertTrue(pkb.getModifiedByStmt(3).contains("a"),
                        "instrukcja call (stmt 3) powinna modyfikować a – propagacja z foo"),

                () -> assertTrue(pkb.getModifiedByProc("foo").contains("a"),
                        "procedura foo powinna modyfikować a")
        );
    }
    
}



