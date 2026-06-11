package de.dlr.shepard.v2.containers.spi;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.file.io.PayloadVersionIO;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * V2CONV-A3 — the dispatch seam behind the unified {@code /v2/containers}
 * surface. One handler per container <em>kind</em> ({@code file},
 * {@code timeseries}, {@code structured-data}, and — once its module ships its
 * own handler — {@code hdf}). The dispatching {@code ContainersV2Service}
 * discovers every handler via CDI {@code @Any Instance<ContainerKindHandler>}
 * and routes a request to the one whose {@link #kind()} matches the
 * {@code ?kind=} query param (for create/list) or whose {@link #owns(BasicContainer)}
 * returns {@code true} (for get/patch/delete, where the kind is derived from the
 * loaded entity).
 *
 * <h2>Why an SPI rather than static delegation</h2>
 *
 * <p>This is the direct sibling of {@code ReferenceKindHandler} (V2CONV-A2). A
 * single {@code ContainersV2Service} cannot statically delegate to every
 * per-kind container service, because {@code hdf} (and any future plugin
 * payload kind) lives in a separate {@code shepard-plugin-*} module that
 * depends on core — core cannot import back into it. The plugin-host contract
 * (CLAUDE.md "plugins build on the /v2/ surface") and the existing registry
 * prior art ({@code ReferenceKindHandler}, {@code ViewRecipeRendererRegistry},
 * {@code AiRegistry}) make a discovery SPI the right shape: core kinds
 * implement this interface in-tree (delegating to the existing per-kind
 * container services); plugin kinds implement it in their own module and are
 * picked up by the same CDI scan.
 *
 * <h2>Scope</h2>
 *
 * <p>The unified surface converges only the homogeneous create / get-one /
 * patch / delete / list operations. Genuinely kind-specific operations stay at
 * their own paths and are NOT routed here: timeseries data / chart-view /
 * anomaly endpoints, file-container payload / content / presigned-url, the hdf
 * browse surface, etc.
 *
 * <h2>Permission contract</h2>
 *
 * <p>The dispatching resource performs the {@code PermissionsService} gate
 * against the resolved container's own numeric id <em>before</em> invoking
 * mutating handler methods — the handler must still defensively validate but
 * never re-implement the auth walk.
 */
public interface ContainerKindHandler {
  /**
   * The kind token this handler serves, e.g. {@code "file"},
   * {@code "timeseries"}, {@code "structured-data"}, {@code "hdf"}. Unique
   * across all registered handlers — a duplicate is a fail-fast packaging
   * defect (mirrors the {@code ReferenceKindHandler} duplicate-kind contract).
   *
   * @return the lower-case kind token; never null/blank.
   */
  String kind();

  /**
   * Whether this handler owns the given persisted container entity. The
   * dispatcher calls this to map an {@code appId}-resolved entity back to its
   * kind for get/patch/delete. Typically an {@code instanceof} check against
   * the handler's concrete entity type.
   *
   * @param container the loaded entity (never null).
   * @return true when this handler is the authority for {@code container}.
   */
  boolean owns(BasicContainer container);

  /**
   * Resolve a container by its {@code appId} (UUID v7), or {@code null} when no
   * container of this kind carries that appId. Used by get/patch/delete
   * dispatch and by the cross-kind {@code owns()} resolution loop.
   *
   * @param appId UUID v7 of the container.
   * @return the entity, or {@code null} when not found for this kind.
   */
  BasicContainer findByAppId(String appId);

  /**
   * Project a persisted container of this kind to the unified wire shape. The
   * handler fills {@link ContainerV2IO#getPayload()} with its per-kind
   * read-only fields (e.g. {@code oid}, {@code defaultCollectionIdList}).
   *
   * @param container the entity this handler owns.
   * @return the unified IO.
   */
  ContainerV2IO toIO(BasicContainer container);

  /**
   * Create a new container of this kind. The {@code body} carries the per-kind
   * create payload (today every core kind takes only {@code name}; the optional
   * {@code collectionAppId} default-container association is resolved by the
   * dispatching resource, not here). Returns the unified IO of the created
   * container.
   *
   * @param body the create payload (deterministic per-kind field map; at least {@code name}).
   * @return the unified IO of the created container.
   */
  ContainerV2IO create(Map<String, Object> body);

  /**
   * Apply an RFC 7396 merge-patch to the container of this kind identified by
   * {@code appId}. Only the kind's mutable fields are honoured (today
   * {@code name} and {@code status}); absent keys are left unchanged.
   *
   * @param appId UUID v7 of the container.
   * @param patch the merge-patch field map (null values clear).
   * @return the unified IO reflecting the post-patch state.
   */
  ContainerV2IO patch(String appId, Map<String, Object> patch);

  /**
   * Delete the container of this kind identified by {@code appId}.
   *
   * @param appId UUID v7 of the container.
   */
  void delete(String appId);

  /**
   * List containers of this kind, optionally filtered by a {@code name}
   * substring. Mirrors the per-kind v1 list operation
   * ({@code GET /shepard/api/fileContainers?name=…}) but keyed/projected to the
   * unified envelope.
   *
   * @param nameFilter optional name substring filter; null = no filter.
   * @return the unified IOs (possibly empty, never null).
   */
  List<ContainerV2IO> list(String nameFilter);

  /**
   * V2CONV-A7-HDF — optionally resolve a single downloadable file payload for
   * the container at {@code appId}. This is the converged home for kind-specific
   * raw-file downloads (the migrated {@code /v2/hdf-containers/{appId}/file}
   * surface) behind the generic {@code GET /v2/containers/{appId}/file} route.
   *
   * <p>Default returns {@link Optional#empty()} — a kind with no single-file
   * payload (timeseries, structured-data) leaves the resolver to answer 415. The
   * hdf handler overrides this to stream the raw HDF5 from HSDS; a future
   * file-container convergence would override it too.
   *
   * <p>The dispatching resource has already gated Read on the container before
   * calling this; the handler must still defensively load the entity by
   * {@code appId} (it may be deleted/absent — return {@link Optional#empty()}
   * or throw the kind's not-found shape).
   *
   * @param appId       UUID v7 of the container.
   * @param rangeHeader optional HTTP {@code Range} header passed through to the
   *                    underlying store; may be null.
   * @return the streaming download (caller closes it), or empty when this kind
   *         has no single-file payload.
   */
  default Optional<ContainerFileDownload> downloadFile(String appId, String rangeHeader) {
    return Optional.empty();
  }

  /**
   * APISIMP-PV-UNIFY — optionally return the version history for the named file
   * stored in the container at {@code appId}, ordered by {@code versionNumber}
   * ascending. This is the converged home for payload versioning behind the
   * generic {@code GET /v2/containers/{appId}/files/{name}/versions} route,
   * replacing the per-kind {@code PayloadVersionRest} and
   * {@code StructuredDataPayloadVersionRest} resources.
   *
   * <p>Default returns {@link Optional#empty()} — kinds without file-payload
   * versioning (timeseries, hdf) leave the dispatcher to answer 415. File and
   * structured-data kind handlers override this to query the shared
   * {@link de.dlr.shepard.data.file.daos.PayloadVersionDAO}.
   *
   * <p>The dispatching resource has already gated Read on the container before
   * calling this method.
   *
   * @param appId    UUID v7 of the container.
   * @param fileName the file or entry name as supplied at upload time.
   * @return the version list (may be empty), or {@link Optional#empty()} when
   *         this kind has no file-payload versioning (→ 415).
   */
  default Optional<List<PayloadVersionIO>> listVersions(String appId, String fileName) {
    return Optional.empty();
  }
}
