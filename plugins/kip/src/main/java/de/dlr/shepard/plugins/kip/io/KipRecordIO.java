package de.dlr.shepard.plugins.kip.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * KIP1a/b — the HMC Kernel Information Profile record served at
 * {@code GET /v2/.well-known/kip/{pid-suffix}}.
 *
 * <p>Shape per {@code aidocs/66 §3.2} — a thin JSON-LD-flavoured
 * record that any HMC PID resolver can consume. Unauthenticated
 * surface (capability metadata, not entity payload) — the
 * {@link KernelInformationProfile#landingPage} URL the record points
 * at may still require auth, which is correct per
 * {@code aidocs/66 §4.2}.
 *
 * <p>The record wraps a small {@code kernelInformationProfile}
 * envelope per the HMC spec: top-level {@code @context} +
 * {@code id} (the PID itself); the substantive fields live in the
 * {@link KernelInformationProfile} nested record.
 *
 * <p>KIP1g moved this record from in-tree
 * {@code de.dlr.shepard.v2.publish.io.KipRecordIO} to the
 * {@code shepard-plugin-kip} module — the HMC-flavoured record
 * shape belongs alongside the resolver per CLAUDE.md's
 * plugin-first heuristic #2 ("New external integrations → plugin
 * shape").
 *
 * <p>KIP1h additively grew the envelope with a
 * {@link KernelInformationProfile#digitalObjectVersion} field
 * — the Phase-1 version segment ({@code v<n>}) the
 * {@code LocalMinter} encodes in its PID. Clients that don't parse
 * the new field ignore it per JSON-LD's open-world semantics; pre-KIP1h
 * rows surface as {@code "v1"} via the resolver's defaulting logic.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "@context", "id", "kernelInformationProfile" })
@Schema(name = "KipRecord", description = "HMC Kernel Information Profile record per aidocs/66 §3.2.")
public record KipRecordIO(
  @JsonProperty("@context") @Schema(description = "JSON-LD @context (constant for KIP).") String context,
  @Schema(required = true, description = "The PID itself (e.g. 'shepard:dlr.de/shepard-prod:data-objects:01HF...:v1').")
  String id,
  @Schema(required = true, description = "The KIP profile body — see KernelInformationProfile.")
  KernelInformationProfile kernelInformationProfile
) {
  public static final String JSONLD_CONTEXT = "https://hmc.helmholtz.de/kip/v1";

  /**
   * Nested envelope carrying the KIP fields per {@code aidocs/66 §3}.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "KernelInformationProfile", description = "KIP record body.")
  public record KernelInformationProfile(
    @Schema(required = true, description = "PID — same value as the parent KipRecordIO#id.") String id,
    @Schema(required = true, description = "Fully-qualified URL of the published entity at this shepard instance.")
    String landingPage,
    @Schema(required = true, description = "KIP digitalObjectType IRI — see aidocs/66 §3.")
    String digitalObjectType,
    @Schema(description = "Server-side wall-clock at the moment the PID was minted (ISO-8601).") String dateCreated,
    @Schema(description = "Server-side wall-clock of the entity's last update (ISO-8601). May equal dateCreated for unchanged entities.")
    String dateModified,
    @Schema(description = "Username of the publisher — KIP rightsHolder.") String rightsHolder,
    @Schema(description = "License string ('CC-BY-4.0', etc.). Null unless an operator sets a default or the caller supplies one (KIP1e).")
    String license,
    @Schema(description = "KIP1h Phase-1 version segment of the published entity ('v1', 'v2', ...).")
    String digitalObjectVersion
  ) {}
}
