package de.dlr.shepard.plugins.unhide.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Date;
import java.util.List;

/**
 * UH1a — single feed entry in
 * {@code GET /v2/unhide/feed.jsonld}.
 *
 * <p>Field order follows the JSON-LD convention of {@code @id} +
 * {@code @type} first so a human eyeballing the feed can scan the
 * entity boundaries without re-orienting on every entry.
 *
 * <p>The schema.org core (name / description / dateCreated /
 * dateModified / license / creator) maps directly from
 * {@code Collection}'s fields. The {@code m4i:isAbout} placeholder
 * is empty in Phase 1 — UH1b enriches it with the
 * PROV1h-rendered processing-step trail once that landing-page
 * exists.
 *
 * <p>{@code @JsonInclude(NON_NULL)} so optional fields drop
 * gracefully — a Collection without a description, license, or
 * creator just omits those JSON members rather than emitting
 * {@code "license": null} which Unhide's inward-mappings would
 * try to reify as a {@code dcat:License} URI.
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
  "m4i:isAbout"
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
  @JsonProperty("m4i:isAbout") List<Object> isAbout
) {}
