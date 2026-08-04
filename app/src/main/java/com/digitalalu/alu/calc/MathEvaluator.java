package com.digitalalu.alu.calc;

public class MathEvaluator {

    public static double eval(final String str, final double h, final double w, final double q, final double sutterH, final double sutterW) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseLogicalOr();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char)ch);
                return x;
            }

            // Logical OR (lowest precedence)
            double parseLogicalOr() {
                double x = parseLogicalAnd();
                for (;;) {
                    if (eat('|')) {
                        if (ch == '|') nextChar(); // eat second '|'
                        double y = parseLogicalAnd();
                        x = (x != 0 || y != 0) ? 1.0 : 0.0;
                    } else {
                        return x;
                    }
                }
            }

            // Logical AND
            double parseLogicalAnd() {
                double x = parseComparison();
                for (;;) {
                    if (eat('&')) {
                        if (ch == '&') nextChar(); // eat second '&'
                        double y = parseComparison();
                        x = (x != 0 && y != 0) ? 1.0 : 0.0;
                    } else {
                        return x;
                    }
                }
            }

            // Comparisons: <, >, <=, >=, ==, !=
            double parseComparison() {
                double x = parseExpression();
                for (;;) {
                    if (eat('=')) {
                        if (ch == '=') nextChar(); // eat second '='
                        x = (x == parseExpression()) ? 1.0 : 0.0;
                    } else if (eat('!')) {
                        if (eat('=')) {
                            x = (x != parseExpression()) ? 1.0 : 0.0;
                        } else {
                            throw new RuntimeException("Unexpected character after !");
                        }
                    } else if (eat('>')) {
                        if (eat('=')) {
                            x = (x >= parseExpression()) ? 1.0 : 0.0;
                        } else {
                            x = (x > parseExpression()) ? 1.0 : 0.0;
                        }
                    } else if (eat('<')) {
                        if (eat('=')) {
                            x = (x <= parseExpression()) ? 1.0 : 0.0;
                        } else {
                            x = (x < parseExpression()) ? 1.0 : 0.0;
                        }
                    } else {
                        return x;
                    }
                }
            }

            // Addition and Subtraction
            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            // Multiplication, Division and Modulo
            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*') || eat('x') || eat('X')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else if (eat('%')) x %= parseFactor();
                    else return x;
                }
            }

            // Unary operators, Parentheses, Variables, Functions, Numbers
            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) { // parentheses
                    x = parseLogicalOr();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) { // variables or functions
                    while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) nextChar();
                    String name = str.substring(startPos, this.pos).toLowerCase();
                    if (eat('(')) { // function call
                        double arg1 = parseLogicalOr();
                        double arg2 = 0;
                        if (eat(',')) {
                            arg2 = parseLogicalOr();
                        }
                        eat(')');
                        if (name.equals("min")) x = Math.min(arg1, arg2);
                        else if (name.equals("max")) x = Math.max(arg1, arg2);
                        else if (name.equals("sqrt")) x = Math.sqrt(arg1);
                        else if (name.equals("abs")) x = Math.abs(arg1);
                        else throw new RuntimeException("Unknown function: " + name);
                    } else { // variables
                        if (name.equals("h")) x = h;
                        else if (name.equals("w")) x = w;
                        else if (name.equals("q") || name.equals("sutterqty") || name.equals("shutterqty") || name.equals("nos")) x = q;
                        else if (name.equals("sutterh") || name.equals("shutterh")) x = sutterH;
                        else if (name.equals("sutterw") || name.equals("shutterw")) x = sutterW;
                        else if (name.equals("sutter") || name.equals("shutter")) {
                            // Fallback depending on whether we have sutterW or sutterH available
                            x = (sutterW > 0) ? sutterW : sutterH;
                        } else x = 0;
                    }
                } else {
                    throw new RuntimeException("Unexpected character: " + (char)ch);
                }

                return x;
            }
        }.parse();
    }
}
