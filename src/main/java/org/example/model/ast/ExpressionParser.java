package org.example.model.ast;

import org.example.model.enums.EntityType;
import java.util.*;

public class ExpressionParser {

    private List<String> tokens;
    private int pos;

    public static TNode parse(String expr) {
        ExpressionParser parser = new ExpressionParser();
        parser.tokens = parser.tokenize(expr);
        parser.pos = 0;
        return parser.parseExpr();
    }

    private List<String> tokenize(String expr) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (char c : expr.toCharArray()) {
            if (Character.isWhitespace(c)) {
                continue;
            } else if ("()+-*".indexOf(c) >= 0) {
                if (sb.length() > 0) {
                    result.add(sb.toString());
                    sb.setLength(0);
                }
                result.add(String.valueOf(c));
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            result.add(sb.toString());
        }
        return result;
    }

    private String peek() {
        if (pos < tokens.size()) return tokens.get(pos);
        return null;
    }

    private String next() {
        return tokens.get(pos++);
    }

    private TNode parseExpr() {
        TNode left = parseTerm();
        while (peek() != null && (peek().equals("+") || peek().equals("-"))) {
            String op = next();
            EntityType opType = op.equals("+") ? EntityType.PLUS : EntityType.MINUS;
            TNode opNode = new TNode(opType);
            opNode.setFirstChild(left);
            TNode right = parseTerm();
            left.setRightSibling(right);
            left = opNode;
        }
        return left;
    }

    private TNode parseTerm() {
        TNode left = parseFactor();
        while (peek() != null && peek().equals("*")) {
            next();
            TNode opNode = new TNode(EntityType.TIMES);
            opNode.setFirstChild(left);
            TNode right = parseFactor();
            left.setRightSibling(right);
            left = opNode;
        }
        return left;
    }

    private TNode parseFactor() {
        String token = peek();
        if (token == null) {
            throw new RuntimeException("Unexpected end of input");
        }
        if (token.equals("(")) {
            next();
            TNode node = parseExpr();
            if (!next().equals(")")) throw new RuntimeException("Expected ')'");
            return node;
        } else if (token.matches("\\d+")) {
            next();
            TNode node = new TNode(EntityType.CONSTANT);
            node.setAttr(token);
            return node;
        } else {
            next();
            TNode node = new TNode(EntityType.VARIABLE);
            node.setAttr(token);
            return node;
        }
    }
}
