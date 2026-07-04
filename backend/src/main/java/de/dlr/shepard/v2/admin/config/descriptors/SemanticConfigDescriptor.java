package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.semantic.io.SemanticConfigIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * V2CONV-A4 — {@link ConfigDescriptor} for the {@code :SemanticConfig}
 * singleton, exposed as {@code GET|PATCH /v2/admin/config/semantic}. Replaces
 * the bespoke {@code SemanticConfigRest} (deleted). Delegates unchanged to
 * {@link OntologyConfigService}.
 *
 * <p>Merge semantics mirror the bespoke resource exactly: for most fields a
 * present value replaces and an absent member leaves alone; for the nullable
 * string fields ({@code defaultVocabularyAppId}, {@code suggestionModelId},
 * {@code annotationDeletePolicy}) a present blank string clears (reverts to
 * default). {@code annotationMode} is validated against
 * {STRICT, PERMISSIVE}; {@code annotationDeletePolicy} against the three
 * delete-policy tokens.
 */
@ApplicationScoped
public class SemanticConfigDescriptor implements ConfigDescriptor<SemanticConfigIO> {

  static final String PROBLEM_TYPE_BAD_MODE = "/problems/semantic.config.bad-annotation-mode";
  static final String PROBLEM_TYPE_BAD_DELETE_POLICY = "/problems/semantic.config.bad-annotation-delete-policy";

  static final Set<String> VALID_DELETE_POLICIES = Set.of("author-or-manager", "author-only", "manager-only");

  @Inject
  OntologyConfigService configService;

  @Inject
  AuthenticationContext authenticationContext;

  @Override
  public String featureName() {
    return "semantic";
  }

  @Override
  public String description() {
    return "Semantic / ontology runtime config: preseed, disabled bundles, annotation mode and policies.";
  }

  @Override
  public SemanticConfigIO currentShape() {
    return SemanticConfigIO.from(configService.loadSingleton());
  }

  @Override
  public SemanticConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    // Validate before loading the singleton to avoid partial mutation.
    if (patch.has("annotationMode") && !patch.get("annotationMode").isNull()) {
      String mode = patch.get("annotationMode").asText().trim().toUpperCase();
      if (!mode.equals("STRICT") && !mode.equals("PERMISSIVE")) {
        throw ConfigPatchException.badRequest(
          PROBLEM_TYPE_BAD_MODE,
          "Invalid annotationMode",
          "annotationMode must be 'STRICT' or 'PERMISSIVE'; got: " + patch.get("annotationMode").asText()
        );
      }
    }
    if (patch.has("annotationDeletePolicy") && !patch.get("annotationDeletePolicy").isNull()) {
      String raw = patch.get("annotationDeletePolicy").asText();
      if (!raw.isBlank()) {
        String policy = raw.trim().toLowerCase();
        if (!VALID_DELETE_POLICIES.contains(policy)) {
          throw ConfigPatchException.badRequest(
            PROBLEM_TYPE_BAD_DELETE_POLICY,
            "Invalid annotationDeletePolicy",
            "annotationDeletePolicy must be one of 'author-or-manager', 'author-only', 'manager-only'; got: " + raw
          );
        }
      }
    }

    SemanticConfig cfg = configService.loadSingleton();
    String actor = authenticationContext == null ? null : authenticationContext.getCurrentUserName();

    if (present(patch, "preseedEnabled")) {
      cfg.setPreseedEnabled(patch.get("preseedEnabled").asBoolean());
    }
    if (present(patch, "disabledBundles")) {
      List<String> bundles = new ArrayList<>();
      patch.get("disabledBundles").forEach(n -> bundles.add(n.asText()));
      cfg.setDisabledBundles(bundles);
    }
    if (present(patch, "defaultVocabularyAppId")) {
      String v = patch.get("defaultVocabularyAppId").asText();
      cfg.setDefaultVocabularyAppId(v.isBlank() ? null : v);
    }
    if (present(patch, "annotationMode")) {
      cfg.setAnnotationMode(patch.get("annotationMode").asText().trim().toUpperCase());
    }
    if (present(patch, "suggestionEnabled")) {
      cfg.setSuggestionEnabled(patch.get("suggestionEnabled").asBoolean());
    }
    if (present(patch, "suggestionModelId")) {
      String v = patch.get("suggestionModelId").asText();
      cfg.setSuggestionModelId(v.isBlank() ? null : v);
    }
    if (present(patch, "personalVocabulariesEnabled")) {
      cfg.setPersonalVocabulariesEnabled(patch.get("personalVocabulariesEnabled").asBoolean());
    }
    if (present(patch, "annotationDeletePolicy")) {
      String v = patch.get("annotationDeletePolicy").asText();
      cfg.setAnnotationDeletePolicy(v.isBlank() ? null : v.trim().toLowerCase());
    }

    cfg.setUpdatedAt(System.currentTimeMillis());
    cfg.setUpdatedBy(actor);

    SemanticConfig saved = configService.patchConfig(cfg);
    Log.infof(
      "V2CONV-A4/semantic: config updated by '%s' (preseedEnabled=%b, annotationMode=%s, suggestionEnabled=%b, " +
        "annotationDeletePolicy=%s, personalVocabulariesEnabled=%b)",
      actor, saved.isPreseedEnabled(), saved.getAnnotationMode(), saved.isSuggestionEnabled(),
      saved.getAnnotationDeletePolicy(), saved.isPersonalVocabulariesEnabled()
    );
    return SemanticConfigIO.from(saved);
  }

  /**
   * Mirrors the bespoke resource's "null = leave unchanged" semantics: a member
   * counts as a write only when present AND non-null. (The nullable string
   * fields use a blank string — not JSON null — to request a clear.)
   */
  private static boolean present(JsonNode patch, String field) {
    return patch.has(field) && !patch.get(field).isNull();
  }
}
