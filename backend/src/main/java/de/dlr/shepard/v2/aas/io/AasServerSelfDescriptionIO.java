package de.dlr.shepard.v2.aas.io;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for {@code GET /v2/aas/.well-known/aas-server}, per
 * {@code aidocs/52 §4a.5}. Self-description document that lets an
 * AAS-aware client discover what this shepard instance exposes,
 * without going through a central registry.
 *
 * <p>No auth — the payload only carries what a public Shell-list
 * would already reveal (capability flags + counts, never per-Shell
 * identifiers).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AasServerSelfDescription")
public class AasServerSelfDescriptionIO {

  @Schema(
    required = true,
    description = "True when `shepard.aas.enabled=true`. False = AAS integration is opt-out at this " +
    "instance; the well-known endpoint stays reachable so discovery still works (clients see " +
    "`enabled=false` and skip)."
  )
  private boolean enabled;

  @Schema(
    required = true,
    description = "AAS API profile this shepard speaks. v1 ships read-only Submodel-Repository per " +
    "`aidocs/52 §4a.5` (`Submodel-Repository-Read-3.1`)."
  )
  private String aasApiProfile;

  @Schema(
    required = true,
    description = "Endpoints exposed by this shepard, keyed by capability name. Empty until the " +
    "AAS1a Shell-/ Submodel-repository slice lands; clients should treat absent keys as " +
    "unsupported rather than assume defaults."
  )
  private Map<String, String> endpoints;

  @Schema(
    required = true,
    description = "Submodel template identifiers this instance accepts as well-formed bodies. " +
    "Sourced from `ShepardTemplate` rows with `templateKind=AAS_SUBMODEL_TEMPLATE` (non-retired). " +
    "Template `name` is used as the identifier until AAS1b lifts the `semanticId` to a first-class " +
    "column."
  )
  private List<String> supportedSubmodelTemplates;

  @Schema(
    required = true,
    description = "Total :AssetAdministrationShell count on this instance. Zero until AAS1a lands. " +
    "Anonymous count only — no per-Shell identifiers are leaked through the well-known endpoint."
  )
  private long shellCount;

  @Schema(
    required = true,
    description = "External AAS Registry registrations advertised for this instance, per " +
    "`aidocs/52 §4a.1`. Empty until AAS1-reg lands."
  )
  private List<RegistryRegistration> registryRegistrations;

  /**
   * One row in {@link #registryRegistrations} — flags an external
   * IDTA AAS Registry / Repository where this shepard has registered
   * its Shells.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "AasRegistryRegistration")
  public static class RegistryRegistration {

    @Schema(required = true, description = "Registry base URL.")
    private String url;

    @Schema(required = true, description = "Registry kind: `idta-registry`, `parent-repository`, `edc`, `mdns`.")
    private String kind;
  }
}
