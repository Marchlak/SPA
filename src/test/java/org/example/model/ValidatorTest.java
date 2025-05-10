package org.example.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.example.model.queryProcessor.Validator;

class ValidatorTest {

    @Test
    void testValidSimpleSelect() {
        Validator validator = new Validator();
        String query = "assign a; select a";
        assertTrue(validator.isValid(query));
    }



    @Test
    void testInvalidMissingSelect() {
        Validator validator = new Validator();
        String query = "assign a; a such that modifies(a, a)";
        assertFalse(validator.isValid(query));
    }

    @Test
    void testInvalidMalformedSuchThat() {
        Validator validator = new Validator();
        String query = "assign a; select a such that modifies(a v)";
        assertFalse(validator.isValid(query));
    }

//    @Test
//    void testInvalidUnknownSynonymInSuchThat() {
//        Validator validator = new Validator();
//        String query = "assign a; variable v; select a such that parent(a, v)";
//        assertFalse(validator.isValid(query));
//    }

    @Test
    void testValidBooleanWithSynonym() {
        Validator validator = new Validator();
        String query = "assign a; select boolean";
        assertTrue(validator.isValid(query));
    }


        @Test
    void testValidSuchThatParentAndParentStar() {
        Validator validator = new Validator();
        String query = "assign a; select a such that parent(a, _) and parent*(a, _)";
        assertTrue(validator.isValid(query));
    }

    @Test
    void testValidSuchThatFollowsAndFollowsStar() {
        Validator validator = new Validator();
        String query = "while w; select w such that follows(w, 1) and follows*(2, w)";
        assertTrue(validator.isValid(query));
    }

    @Test
    void testValidSuchThatCallsAndCallsStar() {
        Validator validator = new Validator();
        String query = "procedure p; select p such that calls(p, \"PROC2\") and calls*(p, \"PROC3\")";
        assertTrue(validator.isValid(query));
    }

    @Test
    void testValidSuchThatDifferentRelationsCombined() {
        Validator validator = new Validator();
        String query = "assign a; variable v; stmt s; select s such that modifies(a, v) and parent(s, 5) and uses(v, a)";
        assertTrue(validator.isValid(query));
    }

    @Test
    void testValidSuchThatWithUnderscores() {
        Validator validator = new Validator();
        String query = "assign a; select a such that parent(_, _) and uses(_, _)";
        assertTrue(validator.isValid(query));
    }

    @Test
    void testValidSuchThatMixedEntityAndStmtRel() {
        Validator validator = new Validator();
        String query = "assign a; variable v; select a such that uses(v, a) and follows(3, 4)";
        assertTrue(validator.isValid(query));
    }

//    @Test
//    void testValidSuchThatMixedEntityAndStmtRel2() {
//        Validator validator = new Validator();
//        String query = "stmt s; var v; Select s such that Follows (s, 7) and Follows* (s, 8) with s.stmt# = 6 and v.varName = \"a\" ";
//        assertTrue(validator.isValid(query));
//    }

    @Test
    void testValidSuchThatMultiple() {
        Validator validator = new Validator();
        String query = "assign s1; variable v; select s1 such that modifies(s1, v) and uses(s1, v)";
        assertTrue(validator.isValid(query));
    }



        @Test
        void testWithOnly() {
            Validator v = new Validator();
            String q = "stmt s; select s with s.stmt# = 3";
            assertTrue(v.isValid(q));
        }
        @Test
        void testSuchThatThenWith() {
            Validator v = new Validator();
            String q = "stmt s; var v; select s such that follows(1, s) with v.varName = \"X\"";
            assertTrue(v.isValid(q));
        }
        @Test
        void testInterleavedClauses() {
            Validator v = new Validator();
            String q = "assign a; var v; select a such that modifies(a, v) with a.stmt# = 5 such that uses(v, a) with v.varName = \"Y\" and a.stmt# = 5";
            assertTrue(v.isValid(q));
        }
        @Test
        void testManyAndsInWith() {
            Validator v = new Validator();
            String q = "procedure p; var v; select p with p.procName = \"MAIN\" and v.varName = \"X\" and p.procName = \"MAIN\"";
            assertTrue(v.isValid(q));
        }


    @Test
    void testMultipleSuchThatAndWithClauses() {
        Validator v = new Validator();
        String q = "assign a; var v; procedure p; stmt s; select s such that parent(s, 11) and follows*(s, 13) with v.varName = \"D\" and p.procName = \"CIRCLE\" such that modifies(a, v) and uses(a, v) with s.stmt# = 6 and a.stmt# = 7";
        assertTrue(v.isValid(q));
    }

    @Test
    void testThreeSuchThatBlocksWithTwoWithBlocks() {
        Validator v = new Validator();
        String q = "assign a; var v; procedure p; stmt s; select a such that follows(s, 1) and parent(s, _) such that parent*(s, 2) with v.varName = \"X\" and p.procName = \"MAIN\" such that uses(a, v) and modifies(a, v) with a.stmt# = 10";
        assertTrue(v.isValid(q));
    }



}