package de.dlr.shepard.template.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates a {@link de.dlr.shepard.template.entities.ShepardTemplate}'s
 * JSON DSL body, per {@code aidocs/54 §7} (option (a) — locked by
 * maintainer 2026-05-12).
 *
 * <p>v1 rules — intentionally minimal, expand as the DSL matures:
 *
 * <ol>
 *   <li>Body must parse as JSON.</li>
 *   <li>Top-level must be a JSON object (not array, string, number).</li>
 *   <li>For every supported {@code templateKind}, at least one of the
 *       expected top-level keys must be present (see {@link
 *       #expectedKeys}).</li>
 *   <li>Unknown top-level keys are <strong>tolerated</strong> — the
 *       DSL is open for plugin-supplied extensions. Stricter checks
 *       land in T1d2.</li>
 * </ol>
 *
 * <p>Validation failures throw {@link InvalidTemplateBodyException}
 * carrying a list of human-readable error strings. Callers surface
 * those as RFC 7807 problem+json {@code 400}s.
 */
@ApplicationScoped
public class TemplateBodyValidator {

  /**
   * Expected top-level JSON keys per {@code templateKind}. At least
   * one must be present for the body to be considered well-formed.
   * Empty set = any object is acceptable (e.g. unknown kinds).
   */
  static Set<String> expectedKeys(String templateKind) {
    if (templateKind == null) return Set.of();
    return switch (templateKind) {
      case "COLLECTION_RECIPE" -> Set.of("collection");
      case "DATAOBJECT_RECIPE" -> Set.of("dataObject", "dataobjects");
      case "EXPERIMENT_RECIPE" -> Set.of("experiment", "steps", "phases");
      case "AAS_SUBMODEL_TEMPLATE" -> Set.of("submodel", "submodelElements");
      // Substrate-split chain (aidocs/semantics/98 §1.1):
      // PROCESS_RECIPE — manufacturing/process-chain blueprint (e.g. MFFD AFP layup → bridge welding).
      // VIEW_RECIPE — shape-driven projection recipe used by GET /v2/templates?kind=view
      //               + POST /v2/shapes/render. First concrete consumer: Trace3D (X/Y/Z + scalar).
      case "PROCESS_RECIPE" -> Set.of("process", "steps", "stages");
      case "VIEW_RECIPE" -> Set.of("view", "shape", "renderer");
      default -> Set.of(); // permissive for unknown kinds
    };
  }

  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Throw an {@link InvalidTemplateBodyException} when {@code body} is
   * not a well-formed JSON-DSL recipe for the given {@code templateKind}.
   * Returns silently when the body is OK.
   */
  public void validate(String body, String templateKind) {
    List<String> errors = collectErrors(body, templateKind);
    if (!errors.isEmpty()) {
      throw new InvalidTemplateBodyException(errors);
    }
  }

  /** Variant of {@link #validate} that returns the error list instead of throwing. */
  public List<String> collectErrors(String body, String templateKind) {
    List<String> errors = new ArrayList<>();
    if (body == null || body.isBlank()) {
      errors.add("body is required and must be non-blank JSON");
      return errors;
    }
    JsonNode root;
    try {
      root = mapper.readTree(body);
    } catch (JsonProcessingException e) {
      errors.add("body is not valid JSON: " + e.getOriginalMessage());
      return errors;
    }
    if (root == null || root.isMissingNode() || root.isNull()) {
      errors.add("body must be a JSON object (got null / missing)");
      return errors;
    }
    if (!root.isObject()) {
      errors.add("body must be a JSON object (got " + root.getNodeType().name().toLowerCase() + ")");
      return errors;
    }
    Set<String> expected = expectedKeys(templateKind);
    if (!expected.isEmpty()) {
      boolean anyPresent = expected.stream().anyMatch(root::has);
      if (!anyPresent) {
        errors.add(
          "body for templateKind=" +
          templateKind +
          " must contain at least one of: " +
          String.join(", ", expected)
        );
      }
    }
    return errors;
  }

  /** Indicates the supplied template body failed v1 JSON-DSL validation. */
  public static class InvalidTemplateBodyException extends RuntimeException {

    private final List<String> errors;

    public InvalidTemplateBodyException(List<String> errors) {
      super(String.join("; ", errors));
      this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
      return errors;
    }
  }
}
