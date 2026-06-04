package de.dlr.shepard.v2.references.spi;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import java.util.List;
import java.util.Map;

/**
 * V2CONV-A2 — the dispatch seam behind the unified {@code /v2/references}
 * surface. One handler per payload <em>kind</em> ({@code file},
 * {@code timeseries}, {@code uri}, {@code video}, {@code git}, …). The
 * dispatching {@code ReferencesV2Service} discovers every handler via CDI
 * {@code @Any Instance<ReferenceKindHandler>} and routes a request to the
 * one whose {@link #kind()} matches the {@code ?kind=} query param (for
 * create/list) or whose {@link #owns(BasicReference)} returns {@code true}
 * (for get/patch/delete, where the kind is derived from the loaded entity).
 *
 * <h2>Why an SPI rather than static delegation</h2>
 *
 * <p>The original V2CONV-A2 brief assumed a single {@code ReferencesV2Service}
 * could statically delegate to every per-kind service. That is structurally
 * impossible: {@code video}, {@code git}, and {@code hdf} live in separate
 * {@code shepard-plugin-*} modules that depend on core — core cannot import
 * back into them. The plugin-host contract (CLAUDE.md "plugins build on the
 * /v2/ surface") and the existing registry prior art
 * ({@code ViewRecipeRendererRegistry}, {@code AiRegistry},
 * {@code AnalyticsRegistry}) make a discovery SPI the right shape: core kinds
 * implement this interface in-tree; plugin kinds implement it in their own
 * module and are picked up by the same CDI scan.
 *
 * <h2>Permission contract</h2>
 *
 * <p>Each handler is responsible for resolving the parent DataObject of the
 * reference it owns and is the authority on which DataObject a create/list
 * call targets. The dispatching resource performs the
 * {@code PermissionsService} gate against the resolved DataObject appId
 * <em>before</em> invoking mutating handler methods — the handler must still
 * defensively validate but never re-implement the auth walk.
 */
public interface ReferenceKindHandler {
  /**
   * The kind token this handler serves, e.g. {@code "file"},
   * {@code "timeseries"}, {@code "uri"}, {@code "video"}, {@code "git"}.
   * Unique across all registered handlers — a duplicate is a fail-fast
   * packaging defect (mirrors the {@code ViewRecipeRendererRegistry}
   * duplicate-IRI contract).
   *
   * @return the lower-case kind token; never null/blank.
   */
  String kind();

  /**
   * Whether this handler owns the given persisted reference entity. The
   * dispatcher calls this to map an {@code appId}-resolved entity back to its
   * kind for get/patch/delete. Typically an {@code instanceof} check against
   * the handler's concrete entity type.
   *
   * @param reference the loaded entity (never null).
   * @return true when this handler is the authority for {@code reference}.
   */
  boolean owns(BasicReference reference);

  /**
   * Resolve a reference by its {@code appId} (UUID v7), or {@code null} when
   * no reference of this kind carries that appId. Used by get/patch/delete
   * dispatch and by the cross-kind {@code owns()} resolution loop.
   *
   * @param appId UUID v7 of the reference.
   * @return the entity, or {@code null} when not found for this kind.
   */
  BasicReference findByAppId(String appId);

  /**
   * Project a persisted reference of this kind to the unified wire shape.
   * The handler fills {@link ReferenceV2IO#getPayload()} with its per-kind
   * fields (the deterministic, documented payload map) and sets the
   * discriminator fields ({@code referenceShape}, {@code fileKind}) where
   * applicable.
   *
   * @param reference the entity this handler owns.
   * @return the unified IO.
   */
  ReferenceV2IO toIO(BasicReference reference);

  /**
   * Create a new reference of this kind attached to the DataObject identified
   * by {@code dataObjectAppId}. The {@code body} is the per-kind create payload
   * (the same field set the per-kind IO carried). Binary kinds (file upload)
   * do NOT route here — they keep their multipart entry point.
   *
   * @param dataObjectAppId UUID v7 of the parent DataObject.
   * @param body the create payload (deterministic per-kind field map).
   * @return the unified IO of the created reference.
   */
  ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body);

  /**
   * Apply an RFC 7396 merge-patch to the reference of this kind identified by
   * {@code appId}. Only the kind's mutable fields are honoured; absent keys
   * are left unchanged.
   *
   * @param appId UUID v7 of the reference.
   * @param patch the merge-patch field map (null values clear).
   * @return the unified IO reflecting the post-patch state.
   */
  ReferenceV2IO patch(String appId, Map<String, Object> patch);

  /**
   * Delete the reference of this kind identified by {@code appId}.
   *
   * @param appId UUID v7 of the reference.
   */
  void delete(String appId);

  /**
   * List references of this kind attached to the DataObject identified by
   * {@code dataObjectAppId}, optionally filtered by a kind-specific
   * {@code subKind} (today only {@code file} honours this — the
   * {@code fileKind} sub-discriminator). Implementations ignore
   * {@code subKind} when it does not apply.
   *
   * @param dataObjectAppId UUID v7 of the parent DataObject.
   * @param subKind optional sub-discriminator (e.g. {@code fileKind}); null = no filter.
   * @return the unified IOs (possibly empty, never null).
   */
  List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind);
}
