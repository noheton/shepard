package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.v2.admin.config.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.ConfigRegistry;
import de.dlr.shepard.v2.admin.config.ConfigValidationException;
import de.dlr.shepard.v2.admin.semantic.io.SemanticConfigIO;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * V2CONV-A4 — {@link ConfigDescriptor} for the semantic (ontology) config singleton.
 *
 * <p>Registers under feature name {@code "semantic"} so
 * {@code GET|PATCH /v2/admin/config/semantic} dispatches here.
 * Mirrors the validation logic in {@code SemanticConfigRest}.
 *
 * <p>Patch semantics for this config follow the existing
 * {@code SemanticConfigRest} convention: null in the request body = leave
 * the field unchanged (not "clear to default"). To clear a nullable string
 * field, pass an empty string.
 */
@ApplicationScoped
public class SemanticConfigDescriptor implements ConfigDescriptor {

  static final String FEATURE = "semantic";
  static final String PROBLEM_BAD_MODE = "/problems/semantic.config.bad-annotation-mode";
  static final String PROBLEM_BAD_DELETE_POLICY = "/problems/semantic.config.bad-annotation-delete-policy";

  private static final Set<String> VALID_DELETE_POLICIES = Set.of(
    "author-or-manager", "author-only", "manager-only"
  );

  @Inject
  OntologyConfigService configService;

  void onStart(@Observes StartupEvent event, ConfigRegistry registry) {
    registry.register(this);
  }

  @Override
  public String featureName() {
    return FEATURE;
  }

  @Override
  public Object read() {
    return SemanticConfigIO.from(configService.loadSingleton());
  }

  @Override
  public Object patch(JsonNode node) throws ConfigValidationException {
    if (node == null || node.isNull()) {
      return read();
    }

    // Validate before loading singleton to avoid partial mutation.
    if (node.has("annotationMode") && !node.get("annotationMode").isNull()) {
      String mode = node.get("annotationMode").asText().trim().toUpperCase();
      if (!mode.equals("STRICT") && !mode.equals("PERMISSIVE")) {
        throw new ConfigValidationException(
          PROBLEM_BAD_MODE,
          "Invalid annotationMode",
          "annotationMode must be 'STRICT' or 'PERMISSIVE'; got: " + node.get("annotationMode").asText()
        );
      }
    }

    if (node.has("annotationDeletePolicy") && !node.get("annotationDeletePolicy").isNull()) {
      String raw = node.get("annotationDeletePolicy").asText();
      if (!raw.isBlank()) {
        String policy = raw.trim().toLowerCase();
        if (!VALID_DELETE_POLICIES.contains(policy)) {
          throw new ConfigValidationException(
            PROBLEM_BAD_DELETE_POLICY,
            "Invalid annotationDeletePolicy",
            "annotationDeletePolicy must be one of 'author-or-manager', 'author-only', " +
              "'manager-only'; got: " + raw
          );
        }
      }
    }

    SemanticConfig cfg = configService.loadSingleton();

    // Apply merge-patch: for this config null = leave unchanged (existing behaviour).
    // Empty string = clear for nullable string fields.
    if (node.has("preseedEnabled") && !node.get("preseedEnabled").isNull()) {
      cfg.setPreseedEnabled(node.get("preseedEnabled").asBoolean());
    }

    if (node.has("disabledBundles") && !node.get("disabledBundles").isNull()) {
      JsonNode arr = node.get("disabledBundles");
      List<String> bundles = new ArrayList<>();
      if (arr.isArray()) {
        arr.forEach(el -> { if (!el.isNull()) bundles.add(el.asText()); });
      }
      cfg.setDisabledBundles(bundles);
    }

    if (node.has("defaultVocabularyAppId") && !node.get("defaultVocabularyAppId").isNull()) {
      String val = node.get("defaultVocabularyAppId").asText();
      cfg.setDefaultVocabularyAppId(val.isBlank() ? null : val);
    }

    if (node.has("annotationMode") && !node.get("annotationMode").isNull()) {
      cfg.setAnnotationMode(node.get("annotationMode").asText().trim().toUpperCase());
    }

    if (node.has("suggestionEnabled") && !node.get("suggestionEnabled").isNull()) {
      cfg.setSuggestionEnabled(node.get("suggestionEnabled").asBoolean());
    }

    if (node.has("suggestionModelId") && !node.get("suggestionModelId").isNull()) {
      String val = node.get("suggestionModelId").asText();
      cfg.setSuggestionModelId(val.isBlank() ? null : val);
    }

    if (node.has("personalVocabulariesEnabled") && !node.get("personalVocabulariesEnabled").isNull()) {
      cfg.setPersonalVocabulariesEnabled(node.get("personalVocabulariesEnabled").asBoolean());
    }

    if (node.has("annotationDeletePolicy") && !node.get("annotationDeletePolicy").isNull()) {
      String raw = node.get("annotationDeletePolicy").asText();
      cfg.setAnnotationDeletePolicy(raw.isBlank() ? null : raw.trim().toLowerCase());
    }

    cfg.setUpdatedAt(System.currentTimeMillis());

    SemanticConfig saved = configService.patchConfig(cfg);
    return SemanticConfigIO.from(saved);
  }
}
