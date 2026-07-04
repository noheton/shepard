package de.dlr.shepard.v2.quality.io;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-INDEPENDENCE-PROOF-INPUT-CAP — validates that the {@code @Size(min=1, max=500)}
 * and {@code @NotNull} constraints on {@code IndependenceProofRequestIO.setA/setB} are
 * correctly declared and enforced by the JSR-380 validator.
 */
class IndependenceProofRequestIOTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  // ── happy path ────────────────────────────────────────────────────────────

  @Test
  void valid_withOneElementEach() {
    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(List.of("appid-a1"));
    req.setSetB(List.of("appid-b1"));
    assertThat(validator.validate(req)).isEmpty();
  }

  @Test
  void valid_withExactly500Elements() {
    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(ids("a", 500));
    req.setSetB(ids("b", 500));
    assertThat(validator.validate(req)).isEmpty();
  }

  // ── setA violations ───────────────────────────────────────────────────────

  @Test
  void invalid_whenSetAIsNull() {
    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(null);
    req.setSetB(List.of("appid-b1"));
    assertThat(violatedPaths(validator.validate(req))).contains("setA");
  }

  @Test
  void invalid_whenSetAIsEmpty() {
    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(List.of());
    req.setSetB(List.of("appid-b1"));
    assertThat(violatedPaths(validator.validate(req))).contains("setA");
  }

  @Test
  void invalid_whenSetAExceeds500() {
    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(ids("a", 501));
    req.setSetB(List.of("appid-b1"));
    assertThat(violatedPaths(validator.validate(req))).contains("setA");
  }

  // ── setB violations ───────────────────────────────────────────────────────

  @Test
  void invalid_whenSetBIsNull() {
    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(List.of("appid-a1"));
    req.setSetB(null);
    assertThat(violatedPaths(validator.validate(req))).contains("setB");
  }

  @Test
  void invalid_whenSetBIsEmpty() {
    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(List.of("appid-a1"));
    req.setSetB(List.of());
    assertThat(violatedPaths(validator.validate(req))).contains("setB");
  }

  @Test
  void invalid_whenSetBExceeds500() {
    IndependenceProofRequestIO req = new IndependenceProofRequestIO();
    req.setSetA(List.of("appid-a1"));
    req.setSetB(ids("b", 501));
    assertThat(violatedPaths(validator.validate(req))).contains("setB");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static List<String> ids(String prefix, int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> "appid-" + prefix + i)
        .collect(Collectors.toList());
  }

  private static Set<String> violatedPaths(Set<ConstraintViolation<IndependenceProofRequestIO>> violations) {
    return violations.stream()
        .map(v -> v.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}
