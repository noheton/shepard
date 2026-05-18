package de.dlr.shepard.aas.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateBodyValidator;
import de.dlr.shepard.template.services.TemplateBodyValidator.InvalidTemplateBodyException;
import de.dlr.shepard.v2.aas.io.AasIdtaImportResultIO;
import de.dlr.shepard.v2.template.io.ShepardTemplateIO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads the three bundled IDTA Submodel Templates from the classpath and
 * upserts them into the {@link ShepardTemplate} graph via
 * {@link ShepardTemplateDAO} (AAS1d).
 *
 * <p>Import is <em>idempotent</em>: a template is skipped when a live
 * (non-retired) record with the same {@code name + templateKind} already
 * carries identical {@code body}, {@code description}, and {@code tags}.
 * When the bundled copy differs from the live record the prior row is
 * retired and a copy-on-write successor is minted (e.g. when upgrading
 * to a newer shepard release that ships a revised template body).
 *
 * <p>Templates are validated by {@link TemplateBodyValidator} before any
 * write is performed; the entire import aborts if validation fails on any
 * entry (which would indicate a corrupt bundled YAML).
 *
 * <p>Classpath resource: {@code aas-idta-templates.yaml} — three entries
 * matching the {@code TemplateImportEntry} shape used by
 * {@code TemplatePortabilityRest}. Body DSL per {@code aidocs/52 §6.1}:
 * <pre>{@code
 * {
 *   "submodel": { "semanticId": "<IRI>", "version": "<v>", "revision": "<r>" },
 *   "submodelElements": [
 *     { "idShort": "Name", "kind": "Property|...", "valueType": "xs:string|...",
 *       "semanticId": "<IRDI or IRI>", "required": true|false, "description": "..." }
 *   ]
 * }
 * }</pre>
 */
@ApplicationScoped
public class AasIdtaTemplateImportService {

  static final String BUNDLED_YAML = "aas-idta-templates.yaml";

  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  @Inject
  ShepardTemplateDAO templateDAO;

  @Inject
  TemplateBodyValidator bodyValidator;

  /**
   * Run the idempotent import and return a summary of what changed.
   *
   * @param caller  username to stamp on newly created rows (typically "system"
   *                for the CLI trigger path, or the authenticated admin's name)
   * @return result with list of created/updated templates and count of skipped
   * @throws IllegalStateException if the bundled YAML cannot be read or parsed
   * @throws InvalidTemplateBodyException if any bundled template body is invalid
   */
  public AasIdtaImportResultIO importBundledTemplates(String caller) {
    List<TemplateImportEntry> entries = loadBundledEntries();

    long now = System.currentTimeMillis();
    List<ShepardTemplateIO> created = new ArrayList<>();
    int skipped = 0;

    for (TemplateImportEntry entry : entries) {
      bodyValidator.validate(entry.body(), entry.templateKind());

      Optional<ShepardTemplate> existing =
          templateDAO.findLatestByName(entry.name(), entry.templateKind());

      if (existing.isPresent() && isIdentical(existing.get(), entry)) {
        skipped++;
        continue;
      }

      ShepardTemplate toSave;
      if (existing.isPresent()) {
        ShepardTemplate prior = existing.get();
        prior.setRetired(true);
        prior.setUpdatedAt(now);
        templateDAO.createOrUpdate(prior);
        toSave = templateDAO.nextVersionOf(prior);
      } else {
        toSave = new ShepardTemplate(entry.name(), entry.templateKind(), entry.body());
      }

      toSave.setBody(entry.body());
      toSave.setDescription(entry.description());
      toSave.setTags(entry.tags() != null ? new ArrayList<>(entry.tags()) : new ArrayList<>());
      toSave.setCreatedBy(caller);
      toSave.setCreatedAt(now);
      toSave.setUpdatedAt(now);

      ShepardTemplate saved = templateDAO.createOrUpdate(toSave);
      created.add(ShepardTemplateIO.from(saved));
    }

    return new AasIdtaImportResultIO(created, skipped);
  }

  private List<TemplateImportEntry> loadBundledEntries() {
    InputStream in = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream(BUNDLED_YAML);
    if (in == null) {
      throw new IllegalStateException("Bundled IDTA template YAML not found on classpath: " + BUNDLED_YAML);
    }
    try {
      String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return YAML_MAPPER.readValue(
          yaml,
          YAML_MAPPER.getTypeFactory().constructCollectionType(List.class, TemplateImportEntry.class));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse bundled IDTA template YAML: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read bundled IDTA template YAML: " + e.getMessage(), e);
    }
  }

  private boolean isIdentical(ShepardTemplate existing, TemplateImportEntry entry) {
    return Objects.equals(existing.getBody(), entry.body())
        && Objects.equals(existing.getDescription(), entry.description())
        && Objects.equals(
            existing.getTags() == null ? List.of() : existing.getTags(),
            entry.tags() == null ? List.of() : entry.tags());
  }

  /** Minimal deserialization record for one YAML entry. */
  public record TemplateImportEntry(
      String name,
      String templateKind,
      Integer version,
      String description,
      List<String> tags,
      String body) {}
}
