package de.dlr.shepard.v2.admin.semantic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * N1c2 — single-bundle row in the
 * {@code GET /v2/admin/semantic/ontologies} response and the body
 * returned by {@code POST /v2/admin/semantic/ontologies} (upload).
 *
 * <p>Mirrors {@link OntologyConfigService.BundleView} but with the
 * field types Jackson likes and OpenAPI annotations for the
 * `/openapi` surface.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "One pre-seeded or operator-uploaded ontology bundle.")
public class OntologyBundleIO {

  @Schema(description = "Bundle slug — unique across built-in + user namespaces.")
  private String id;

  @Schema(description = "Human-readable name.")
  private String name;

  @Schema(description = "'builtin' for classpath-shipped bundles; 'user' for operator-uploaded.")
  private String source;

  @Schema(description = "When true, the bundle is seeded unconditionally even if it appears in disabledBundles.")
  private boolean required;

  @Schema(description = "Effective enabled state: required wins; otherwise 'not in disabled set'.")
  private boolean enabled;

  @Schema(description = "Canonical IRI prefix the ontology mints terms under.")
  private String iriPrefix;

  @Schema(description = "Canonical refresh URL (nullable for user-uploaded bundles).")
  private String canonicalUrl;

  @Schema(description = "SPDX-ish licence label.")
  private String license;

  @Schema(description = "Hex SHA-256 of the bundle bytes.")
  private String sha256;

  @Schema(description = "Bundle file size in bytes.")
  private long byteSize;

  /** Project a service-layer view into the wire IO. */
  public static OntologyBundleIO from(OntologyConfigService.BundleView v) {
    OntologyBundleIO io = new OntologyBundleIO();
    io.setId(v.id);
    io.setName(v.name);
    io.setSource(v.source);
    io.setRequired(v.required);
    io.setEnabled(v.enabled);
    io.setIriPrefix(v.iriPrefix);
    io.setCanonicalUrl(v.canonicalUrl);
    io.setLicense(v.license);
    io.setSha256(v.sha256);
    io.setByteSize(v.byteSize);
    return io;
  }
}
