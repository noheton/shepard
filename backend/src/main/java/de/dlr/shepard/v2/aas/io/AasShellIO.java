package de.dlr.shepard.v2.aas.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for one IDTA AAS v3 AssetAdministrationShell (AAS1a).
 *
 * <p>Minimal compliant shape: {@code id}, {@code idShort}, {@code assetInformation},
 * optional {@code description} langStringSet, and an empty {@code submodels} list
 * (AAS1b will populate submodels from DataObject payloads).
 *
 * <p>See {@code aidocs/52 §4a} for the AAS1a scope definition.
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
      description = "Submodel references. Empty in AAS1a; populated by AAS1b when DataObject " +
          "payloads are mapped to Submodels.")
  private List<String> submodels;

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
