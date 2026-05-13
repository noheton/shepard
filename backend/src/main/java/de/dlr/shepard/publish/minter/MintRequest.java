package de.dlr.shepard.publish.minter;

import java.util.Collections;
import java.util.Map;

/**
 * Input payload for {@link Minter#mint(MintRequest)} — describes the
 * shepard entity that's about to receive a PID.
 *
 * <p>KIP1a baseline shape per {@code aidocs/66 §3-§5}. Fields kept
 * minimal-by-design: the registry pulls richer fields (creator,
 * licence, keywords, dateCreated/dateModified) from the entity itself
 * when the REST layer builds the request, but the {@link Minter}
 * interface only needs the wire-shape identifiers + the locator URL
 * so an adapter (mock, ePIC, DataCite — KIP1c/d) can issue the call
 * without re-loading the entity.
 *
 * @param entityKind shepard's entity-kind name — one of the
 *                   {@link PublishableKind} ids
 *                   ({@code "data-objects"}, {@code "collections"} in
 *                   KIP1a; future slices add bundles / files /
 *                   lab-journal-entries per {@code aidocs/66 §4.1}).
 * @param appId      the entity's stable application identifier (UUID v7);
 *                   becomes the visible portion of the PID for the mock
 *                   minter, and the minter-specific suffix for ePIC /
 *                   DataCite.
 * @param locatorUrl the fully-qualified URL the PID resolver should
 *                   point at — computed via the {@code aidocs/22}
 *                   pattern as {@code <shepard.url>/v2/{kind}/{appId}}.
 *                   Used by the KIP record's {@code digitalObjectLocator}.
 * @param metadata   bag of additional KIP-flavoured fields the minter
 *                   may stamp on the PID record (KIP1a uses
 *                   {@code digitalObjectType}, {@code dateCreated},
 *                   {@code dateModified}, {@code rightsHolder},
 *                   {@code license}). Immutable view — adapters should
 *                   not mutate.
 */
public record MintRequest(String entityKind, String appId, String locatorUrl, Map<String, String> metadata) {
  public MintRequest {
    if (entityKind == null || entityKind.isBlank()) {
      throw new IllegalArgumentException("entityKind must not be null/blank");
    }
    if (appId == null || appId.isBlank()) {
      throw new IllegalArgumentException("appId must not be null/blank");
    }
    if (locatorUrl == null || locatorUrl.isBlank()) {
      throw new IllegalArgumentException("locatorUrl must not be null/blank");
    }
    metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(metadata));
  }
}
