package com.graden.models.service.tools.builtin;

import com.graden.models.service.tools.Tool;
import com.graden.models.service.tools.ToolDefinition;
import com.graden.models.service.tools.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

public class Calculate implements Tool {

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("expression", Map.of(
                "type", "string",
                "description", "A mathematical expression to evaluate (e.g. '2+3*4', 'sqrt(16)', 'sin(pi/2)'). Supports +, -, *, /, %, ^, sqrt, sin, cos, tan, log, abs, floor, ceil, pi, e."
        ));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", java.util.List.of("expression"));

        return new ToolDefinition(
                "calculate",
                "Evaluates a mathematical expression and returns the result. Supports basic arithmetic, trigonometry, logarithms, and constants (pi, e).",
                parameters);
    }

    @Override
    public ToolResult execute(Map<String, Object> args) throws Exception {
        long start = System.currentTimeMillis();

        String expression = (String) args.get("expression");
        if (expression == null || expression.isBlank()) {
            return ToolResult.failure("Missing required parameter: expression", 0);
        }

        String sanitized = expression.trim();
        if (sanitized.length() > 500) {
            return ToolResult.failure("Expression too long (max 500 chars)", 0);
        }

        double result = evaluate(sanitized);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("expression", expression);
        output.put("result", result);
        output.put("result_string", formatResult(result));

        return ToolResult.success(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(output), System.currentTimeMillis() - start);
    }

    private double evaluate(String expr) {
        return new ExpressionEvaluator(expr).parse();
    }

    private String formatResult(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.format("%.0f", value);
        }
        return String.format("%.10g", value);
    }

    private static class ExpressionEvaluator {

        private final String input;
        private int pos;

        ExpressionEvaluator(String input) {
            this.input = input;
            this.pos = 0;
        }

        double parse() {
            double result = parseExpression();
            if (pos < input.length()) {
                throw new IllegalArgumentException("Unexpected character at position " + pos + ": '" + input.charAt(pos) + "'");
            }
            return result;
        }

        private double parseExpression() {
            double result = parseTerm();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op == '+' || op == '-') {
                    pos++;
                    double right = parseTerm();
                    if (op == '+') result += right;
                    else result -= right;
                } else {
                    break;
                }
            }
            return result;
        }

        private double parseTerm() {
            double result = parsePower();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op == '*' || op == '/' || op == '%') {
                    pos++;
                    double right = parsePower();
                    if (op == '*') result *= right;
                    else if (op == '/') {
                        if (right == 0) throw new ArithmeticException("Division by zero");
                        result /= right;
                    } else result %= right;
                } else {
                    break;
                }
            }
            return result;
        }

        private double parsePower() {
            double result = parseUnary();
            while (pos < input.length() && input.charAt(pos) == '^') {
                pos++;
                double right = parseUnary();
                result = Math.pow(result, right);
            }
            return result;
        }

        private double parseUnary() {
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == '-') {
                pos++;
                return -parseAtom();
            }
            if (pos < input.length() && input.charAt(pos) == '+') {
                pos++;
                return parseAtom();
            }
            return parseAtom();
        }

        private double parseAtom() {
            skipWhitespace();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unexpected end of expression");
            }

            char c = input.charAt(pos);

            if (c == '(') {
                pos++;
                double result = parseExpression();
                skipWhitespace();
                if (pos >= input.length() || input.charAt(pos) != ')') {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                pos++;
                return result;
            }

            if (Character.isDigit(c) || c == '.') {
                return parseNumber();
            }

            return parseFunction();
        }

        private double parseNumber() {
            int start = pos;
            while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
                pos++;
            }
            return Double.parseDouble(input.substring(start, pos));
        }

        private double parseFunction() {
            int start = pos;
            while (pos < input.length() && Character.isLetter(input.charAt(pos))) {
                pos++;
            }
            String name = input.substring(start, pos).toLowerCase();

            skipWhitespace();

            double argument = 0;
            if (pos < input.length() && input.charAt(pos) == '(') {
                pos++;
                argument = parseExpression();
                skipWhitespace();
                if (pos >= input.length() || input.charAt(pos) != ')') {
                    throw new IllegalArgumentException("Missing closing parenthesis for " + name);
                }
                pos++;
            }

            return switch (name) {
                case "pi" -> Math.PI;
                case "e" -> Math.E;
                case "sqrt" -> Math.sqrt(argument);
                case "sin" -> Math.sin(argument);
                case "cos" -> Math.cos(argument);
                case "tan" -> Math.tan(argument);
                case "asin" -> Math.asin(argument);
                case "acos" -> Math.acos(argument);
                case "atan" -> Math.atan(argument);
                case "log" -> Math.log10(argument);
                case "ln" -> Math.log(argument);
                case "abs" -> Math.abs(argument);
                case "floor" -> Math.floor(argument);
                case "ceil" -> Math.ceil(argument);
                case "exp" -> Math.exp(argument);
                default -> throw new IllegalArgumentException("Unknown function or symbol: " + name);
            };
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }
    }
}
