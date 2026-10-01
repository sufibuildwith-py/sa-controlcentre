package com.saproduction.command.eve.cognitive;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Deterministic, safe Arithmetic Capability for EVE Cognitive Runtime 2.0.
 * Directly evaluates expressions such as "30-10+4-12", "17 * 23", "50000 / 4".
 *
 * Operational Invariants:
 * - Zero Java reflection, zero eval/scripting engines, zero shell calls.
 * - Pure recursive descent parsing over bounded tokens.
 * - Execution time < 1ms, preventing expensive 200+ second LLM loops for simple math.
 */
public final class EveArithmeticCapability {

  private EveArithmeticCapability() {}

  /**
   * Checks if an input string looks like or contains an arithmetic expression.
   */
  public static boolean isArithmeticExpression(String input) {
    return extractArithmeticExpression(input).isPresent();
  }

  /**
   * Extracts clean arithmetic subexpression from a user prompt.
   */
  public static Optional<String> extractArithmeticExpression(String input) {
    if (input == null || input.isBlank()) {
      return Optional.empty();
    }
    String s = input.trim();
    // 1. Direct expression
    if (s.matches("^[0-9\\s+\\-*/%().]+$")) {
      boolean hasOperator = s.matches(".*[+\\-*/%].*");
      boolean hasDigit = s.matches(".*[0-9].*");
      if (hasOperator && hasDigit) {
        return Optional.of(s);
      }
    }
    // 2. Embedded expression within conversational prompt (e.g. "30-10+4-12 ka hisaab batao")
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+(?:\\s*[+\\-*/%]\\s*[0-9]+)+)").matcher(s);
    if (m.find()) {
      return Optional.of(m.group(1).trim());
    }
    return Optional.empty();
  }

  /**
   * Safely evaluates an arithmetic expression and returns a cleanly formatted result.
   */
  public static Optional<String> evaluate(String expression) {
    Optional<String> exprOpt = extractArithmeticExpression(expression);
    if (exprOpt.isEmpty()) {
      return Optional.empty();
    }
    try {
      Parser parser = new Parser(exprOpt.get());
      BigDecimal val = parser.parse();
      if (val == null) {
        return Optional.empty();
      }
      // If integer value, format without trailing .00
      if (val.stripTrailingZeros().scale() <= 0) {
        return Optional.of(val.toBigInteger().toString());
      } else {
        return Optional.of(val.stripTrailingZeros().toPlainString());
      }
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private static class Parser {
    private final String str;
    private int pos = -1;
    private int ch;

    Parser(String str) {
      this.str = str;
      nextChar();
    }

    private void nextChar() {
      ch = (++pos < str.length()) ? str.charAt(pos) : -1;
    }

    private boolean eat(int charToEat) {
      while (ch == ' ') nextChar();
      if (ch == charToEat) {
        nextChar();
        return true;
      }
      return false;
    }

    BigDecimal parse() {
      BigDecimal x = parseExpression();
      if (pos < str.length()) {
        throw new IllegalArgumentException("Unexpected char: " + (char) ch);
      }
      return x;
    }

    private BigDecimal parseExpression() {
      BigDecimal x = parseTerm();
      while (true) {
        if (eat('+')) {
          x = x.add(parseTerm());
        } else if (eat('-')) {
          x = x.subtract(parseTerm());
        } else {
          return x;
        }
      }
    }

    private BigDecimal parseTerm() {
      BigDecimal x = parseFactor();
      while (true) {
        if (eat('*')) {
          x = x.multiply(parseFactor());
        } else if (eat('/')) {
          BigDecimal divisor = parseFactor();
          if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
          }
          x = x.divide(divisor, 6, RoundingMode.HALF_UP);
        } else if (eat('%')) {
          BigDecimal divisor = parseFactor();
          if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Modulo by zero");
          }
          x = x.remainder(divisor);
        } else {
          return x;
        }
      }
    }

    private BigDecimal parseFactor() {
      if (eat('+')) return parseFactor(); // unary plus
      if (eat('-')) return parseFactor().negate(); // unary minus

      BigDecimal x;
      int startPos = this.pos;
      if (eat('(')) {
        x = parseExpression();
        eat(')');
      } else if ((ch >= '0' && ch <= '9') || ch == '.') {
        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
        x = new BigDecimal(str.substring(startPos, this.pos));
      } else {
        throw new IllegalArgumentException("Unexpected character in expression: " + (char) ch);
      }
      return x;
    }
  }
}
