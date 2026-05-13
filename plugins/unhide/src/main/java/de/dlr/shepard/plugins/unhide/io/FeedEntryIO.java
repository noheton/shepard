package de.dlr.shepard.plugins.unhide.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Date;
import java.util.List;

/**
 * UH1a / UH1b — single feed entry in
 * {@code GET /v2/unhide/feed.jsonld}.
 *
 * <p>Field order follows the JSON-LD convention of {@code @id} +
 * {@code @type} first so a human eyeballing the feed can scan the
 * entity boundaries without re-orienting on every entry.
 *
 * <p>The schema.org core (name / description / dateCreated /
 * dateModified / license / creator) maps directly from
 * {@code Collection}'s fields.
 *
 * <p>UH1b adds {@code m4i:hasProcessingStep} — the most-recent-N
 * {@code :Activity} rows targeting this Collection, rendered as
 * m4i ProcessingStep node bodies via
 * {@link de.dlr.shepard.provenance.services.ProvJsonLdRenderer#renderActivityAsM4iNode}.
 * Window size is governed by the deploy-time-only
 * {@code shepard.unhide.feed.provenance-window} property (default
 * 5). When a Collection has no activities the field is omitted
 * (JSON-LD treats absence as "no provenance available" — the
 * correct semantics — and Unhide's inward-mappings handle the
 * missing-field case cleanly).
 *
 * <p>{@code @JsonInclude(NON_NULL)} so optional fields drop
 * gracefully — a Collection without a description, license, creator,
 * or processing-step trail just omits those JSON members rather
 * than emitting {@code "license": null} which Unhide's
 * inward-mappings would try to reify as a {@code dcat:License} URI.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "@id",
  "@type",
  "name",
  "description",
  "dateCreated",
  "dateModified",
  "license",
  "creator",
  "m4i:hasProcessingStep"
})
public record FeedEntryIO(
  @JsonProperty("@id") String id,
  @JsonProperty("@type") List<String> type,
  @JsonProperty("name") String name,
  @JsonProperty("description") String description,
  @JsonProperty("dateCreated") Date dateCreated,
  @JsonProperty("dateModified") Date dateModified,
  @JsonProperty("license") String license,
  @JsonProperty("creator") Object creator,
  @JsonProperty("m4i:hasProcessingStep") List<Object> hasProcessingStep
) {}
