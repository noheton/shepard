package de.dlr.shepard.v2.vocabularies.io;

import de.dlr.shepard.context.semantic.entities.Vocabulary;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMA-V6-014 — read projection for a {@link Vocabulary} node.
 *
 * <p>Returned by {@code GET /v2/vocabularies/personal} and
 * {@code POST /v2/vocabularies/personal}.
 * Exposes the caller-visible fields including the two new SEMA-V6-014
 * fields ({@code type} and {@code ownedByUserAppId}).
 */
@Data
@NoArgsConstructor
@Schema(name = "Vocabulary")
public class VocabularyIO {

  @Schema(readOnly = true, description = "UUID v7 application-level identifier.")
  private String appId;

  @Schema(description = "Canonical namespace URI / IRI prefix.")
  private String uri;

  @Schema(description = "Human-readable label.")
  private String label;

  @Schema(description = "Short namespace prefix (e.g. 'dcterms').", nullable = true)
  private String prefix;

  @Schema(description = "Optional free-text description of the vocabulary's scope.", nullable = true)
  private String description;

  @Schema(description = "When false, this vocabulary is hidden from autocomplete and predicate lookup.")
  private boolean enabled;

  @Schema(
    description = "Vocabulary kind: null = system/operator vocabulary; 'PERSONAL' = user-minted personal vocabulary.",
    nullable = true
  )
  private String type;

  @Schema(
    description = "appId of the owning User. Only set when type is 'PERSONAL'.",
    nullable = true
  )
  private String ownedByUserAppId;

  /** Map from entity. */
  public static VocabularyIO from(Vocabulary v) {
    VocabularyIO io = new VocabularyIO();
    io.appId             = v.getAppId();
    io.uri               = v.getUri();
    io.label             = v.getLabel();
    io.prefix            = v.getPrefix();
    io.description       = v.getDescription();
    io.enabled           = v.isEnabled();
    io.type              = v.getType();
    io.ownedByUserAppId  = v.getOwnedByUserAppId();
    return io;
  }
}
