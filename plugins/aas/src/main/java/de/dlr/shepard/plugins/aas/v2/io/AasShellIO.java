package de.dlr.shepard.plugins.aas.v2.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for one IDTA AAS v3 AssetAdministrationShell (AAS1a/AAS1b).
 *
 * <p>Minimal compliant shape: {@code id}, {@code idShort}, {@code assetInformation},
 * optional {@code description} langStringSet, and a {@code submodels} list of
 * {@link AasReferenceIO} objects.
 *
 * <p>AAS1a returns an empty {@code submodels} list. AAS1b populates it from
 * top-level DataObject payloads on single-Shell GET and the submodels endpoint.
 *
 * <p>Note: {@code submodels} type changed from {@code List<String>} to
 * {@code List<AasReferenceIO>} in AAS1b Commit 2 — breaking on the {@code /v2/}
 * surface (permitted per CLAUDE.md API-version policy; was always empty in AAS1a).
 *
 * <p>See {@code aidocs/integrations/52-aas-backend-integration.md §4a} for the scope definition.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AasShell")
public class AasShellIO {

  @Schema(required = true,
      description = "Globally unique IRI for this Shell. Derived from the Collection appId: " +
          "`urn:shepard:collection:{appId}`.")
  private String id;

  @Schema(required = true,
      description = "Short, non-unique identifier per IDTA AAS v3. Sanitised from the " +
          "Collection name: leading digit or special chars replaced so the value matches " +
          "[a-zA-Z][a-zA-Z0-9_]*.")
  private String idShort;

  @Schema(required = true,
      description = "Minimal asset information block per IDTA AAS v3 §7.")
  private AssetInformationIO assetInformation;

  @Schema(description = "Human-readable description list (langString). Sourced from Collection.description. " +
      "Absent when the Collection has no description.")
  private List<LangStringIO> description;

  @Schema(required = true,
      description = "Submodel references (IDTA AAS v3 Reference objects). Empty in AAS1a; " +
          "populated by AAS1b from top-level DataObjects on single-Shell GET. " +
          "Type changed from List<String> to List<AasReference> in AAS1b Commit 2.")
  private List<AasReferenceIO> submodels;

  /** IDTA AAS v3 AssetInformation — minimal form for AAS1a. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "AasAssetInformation")
  public static class AssetInformationIO {

    @Schema(required = true,
        description = "Asset kind. Always `Instance` for Collection-backed Shells in AAS1a.")
    private String assetKind;

    @Schema(required = true,
        description = "Globally unique IRI for the underlying asset. " +
            "Derived from the Collection appId: `urn:shepard:asset:{appId}`.")
    private String globalAssetId;
  }

  /** IDTA AAS v3 LangStringTextType — one element of a multi-lingual description set. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "AasLangString")
  public static class LangStringIO {

    @Schema(required = true, description = "BCP 47 language tag, e.g. `en`.")
    private String language;

    @Schema(required = true, description = "Text value in the given language.")
    private String text;
  }
}
