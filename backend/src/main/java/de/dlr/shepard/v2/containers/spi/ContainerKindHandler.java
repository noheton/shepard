package de.dlr.shepard.v2.containers.spi;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.v2.containers.io.ContainerStatsIO;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.file.io.PayloadVersionIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedUploadRequestIO;
import de.dlr.shepard.v2.filecontainer.io.UploadCommitIO;
import de.dlr.shepard.v2.timeseries.io.TimeseriesAnnotationIO;
import de.dlr.shepard.v2.timeseriescontainer.io.SpatialRolesIO;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesChannelV2IO;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesContainerChartViewIO;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
   * APISIMP-CONTAINERS-LIST-IN-MEMORY-PAGING — count containers without loading
   * all of them. Default delegates to {@link #list(String)} for backward compat;
   * DB-aware handlers override both methods to push COUNT + SKIP/LIMIT to the
   * underlying store.
   *
   * @param nameFilter optional name substring filter; null = no filter.
   * @return total count of matching containers.
   */
  default int count(String nameFilter) {
    return list(nameFilter).size();
  }

  /**
   * APISIMP-CONTAINERS-LIST-IN-MEMORY-PAGING — bounded page of containers.
   * Default slices the full list in memory; DB-aware handlers override for
   * true Cypher/SQL SKIP/LIMIT.
   *
   * @param nameFilter optional name substring filter; null = no filter.
   * @param skip 0-based offset (number of records to skip).
   * @param limit maximum records to return (must be &gt; 0).
   * @return the unified IOs for the requested page (possibly empty, never null).
   */
  default List<ContainerV2IO> list(String nameFilter, int skip, int limit) {
    List<ContainerV2IO> all = list(nameFilter);
    int total = all.size();
    int from = (int) Math.min((long) skip, (long) total);
    int to = (int) Math.min((long) skip + limit, (long) total);
    return all.subList(from, to);
  }

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

  /**
   * APISIMP-CONT-NS-COLLAPSE-1 — optionally return storage statistics for the
   * container at {@code appId}. Converged home for the per-kind stats sub-resource
   * behind the generic {@code GET /v2/containers/{appId}/stats} route, replacing
   * the bespoke {@code TimeseriesContainerStatsRest} resource.
   *
   * <p>Default returns {@link Optional#empty()} — kinds without a stats concept
   * (file, structured-data) leave the dispatcher to answer 415.
   *
   * <p>The dispatching resource has already gated Read on the container before
   * calling this method.
   *
   * @param appId UUID v7 of the container.
   * @return the stats body, or {@link Optional#empty()} when this kind has no
   *         stats concept (→ 415).
   */
  default Optional<ContainerStatsIO> getStats(String appId) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-LDO-UNIFY — optionally return the distinct DataObjects that
   * link to the container at {@code appId} via their references, projected to
   * {@link DataObjectIO}. This is the converged home for the per-kind
   * {@code linked-data-objects} sub-resources behind the generic
   * {@code GET /v2/containers/{appId}/linked-data-objects} route, replacing the
   * three identical {@code FileContainerLinkedDataObjectsRest},
   * {@code StructuredDataContainerLinkedDataObjectsRest} and
   * {@code TimeseriesContainerLinkedDataObjectsRest} GET endpoints.
   *
   * <p>Default returns {@link Optional#empty()} — a kind that does not model
   * linked DataObjects (e.g. a future plugin kind) leaves the dispatcher to
   * answer 415. All three core kinds override this to query their per-kind
   * container service's {@code findLinkedDataObjectsByAppId(...)}.
   *
   * <p>The dispatching resource has already gated Read on the container before
   * calling this method (mirrors the per-kind resources, which gated via
   * {@code getContainerByAppId(...)} first).
   *
   * @param appId UUID v7 of the container.
   * @return the linked DataObjects (may be empty), or {@link Optional#empty()}
   *         when this kind has no linked-DataObject concept (→ 415).
   */
  default Optional<List<DataObjectIO>> listLinkedDataObjects(String appId) {
    return Optional.empty();
  }

  /**
   * APISIMP-LINKED-DO-IN-MEMORY-PAGE — total count of linked DataObjects for
   * this container, used to build the {@code total} field in a paged response
   * without materialising the full list.
   *
   * <p>The default falls back to {@link #listLinkedDataObjects(String)} so that
   * plugin handlers that have not yet overridden this method still work correctly
   * (at the cost of the full-list materialisation). Core kinds override with a
   * dedicated {@code COUNT(DISTINCT do)} Cypher query.
   *
   * @return the total count, or {@link Optional#empty()} when this kind has no
   *         linked-DataObject concept (→ 415).
   */
  default Optional<Long> countLinkedDataObjects(String appId) {
    return listLinkedDataObjects(appId).map(list -> (long) list.size());
  }

  /**
   * APISIMP-LINKED-DO-IN-MEMORY-PAGE — one page of linked DataObjects, ordered
   * by {@code do.appId} and sliced at the DAO level via Cypher
   * {@code SKIP $skip LIMIT $limit}.
   *
   * <p>The default falls back to {@link #listLinkedDataObjects(String)} +
   * in-memory {@code subList} for backward-compatible plugin handlers. Core
   * kinds override with an efficient paged Cypher query.
   *
   * @param skip   number of rows to skip (= {@code page × pageSize})
   * @param limit  maximum rows to return (= {@code pageSize})
   * @return one page of DataObjectIO, or {@link Optional#empty()} when this kind
   *         has no linked-DataObject concept (→ 415).
   */
  default Optional<List<DataObjectIO>> listLinkedDataObjectsPaged(
      String appId, int skip, int limit) {
    return listLinkedDataObjects(appId).map(all -> {
      int total = all.size();
      int fromIdx = Math.min(skip, total);
      int toIdx = Math.min(fromIdx + limit, total);
      return all.subList(fromIdx, toIdx);
    });
  }

  // ── APISIMP-CONT-NS-COLLAPSE-2: channel endpoints ──────────────────────────

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — list channels of a timeseries container.
   * Default returns {@link Optional#empty()} (→ 415 for non-timeseries kinds).
   *
   * @param containerAppId appId of the container.
   * @param page           zero-based page index.
   * @param pageSize       page size (clamped to 1–1000 by the handler).
   * @return the channel listing, or empty when this kind has no channels.
   */
  default Optional<PagedResponseIO<TimeseriesChannelV2IO>> listChannels(
      String containerAppId, int page, int pageSize) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — spatial role assignments for the Trace3D recipe
   * builder. Default returns {@link Optional#empty()} (→ 415 for non-timeseries
   * kinds).
   *
   * @param containerAppId appId of the container.
   * @return the spatial roles map, or empty when this kind has no channels.
   */
  default Optional<SpatialRolesIO> getChannelSpatialRoles(String containerAppId) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — fetch data points for a single channel by
   * {@code shepardId}. Default returns {@link Optional#empty()} (→ 415 for
   * non-timeseries kinds).
   *
   * @param containerAppId appId of the container.
   * @param shepardId      UUID of the channel.
   * @param start          start time in nanoseconds since Unix epoch.
   * @param end            end time in nanoseconds since Unix epoch.
   * @param downsample     optional downsample algorithm name (e.g. "lttb").
   * @param maxPoints      optional max-points cap for downsampling.
   * @return the channel data, or empty when this kind has no channels.
   */
  default Optional<TimeseriesWithDataPoints> getChannelData(
      String containerAppId, UUID shepardId,
      Long start, Long end, String downsample, Integer maxPoints) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — bulk channel data fetch.
   * Default returns {@link Optional#empty()} (→ 415 for non-timeseries kinds).
   *
   * <p>APISIMP-BULK-CHANNEL-REQ-NANOS-TO-ISO — {@code startNs} and {@code endNs}
   * are pre-parsed nanosecond timestamps supplied by the REST resource after it
   * validates the ISO 8601 strings from the request body and returns 400 on error.
   *
   * @param containerAppId appId of the container.
   * @param shepardIds     channel UUIDs to fetch (from the request body).
   * @param startNs        window start in nanoseconds since Unix epoch.
   * @param endNs          window end in nanoseconds since Unix epoch.
   * @return the list of per-channel data, or empty when this kind has no channels.
   */
  default Optional<List<TimeseriesWithDataPoints>> getBulkChannelData(
      String containerAppId, List<UUID> shepardIds, long startNs, long endNs) {
    return Optional.empty();
  }

  // ── APISIMP-CONT-NS-COLLAPSE-5: thumbnail + presigned-url endpoints ─────────

  /**
   * APISIMP-CONT-NS-COLLAPSE-5 — optionally return a PNG thumbnail for the file
   * payload at {@code oid} within the container at {@code appId}. This is the
   * converged home for the thumbnail endpoint behind the generic
   * {@code GET /v2/containers/{appId}/payload/{oid}/thumbnail} route, replacing
   * {@code ThumbnailRest}.
   *
   * <p>Default returns {@link Optional#empty()} (→ 415) for kinds that don't
   * support thumbnails (timeseries, structured-data, hdf). The file kind handler
   * overrides this to call {@code ThumbnailService.getThumbnail(...)}.
   *
   * @param appId      UUID v7 of the container.
   * @param oid        object id (UUID) of the file within the container.
   * @param sizeParam  requested thumbnail size in pixels; null or invalid values
   *                   are normalised to 400.
   * @return a {@code Response} carrying the PNG bytes, or {@link Optional#empty()}
   *         when this kind has no thumbnail concept (→ 415).
   */
  default Optional<Response> getThumbnail(String appId, String oid, Integer sizeParam) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-5 — optionally obtain a presigned PUT URL to upload a
   * file directly to the active storage backend (S3). This is the converged home
   * for the presigned-upload endpoint behind the generic
   * {@code POST /v2/containers/{appId}/upload-url} route, replacing the
   * {@code FileContainerPresignedUrlRest#getUploadUrl} method.
   *
   * <p>Default returns {@link Optional#empty()} (→ 415) for kinds that don't
   * support presigned uploads (timeseries, structured-data, hdf). The file kind
   * handler overrides this to call {@code FileContainerService.presignedUploadUrl(...)}.
   *
   * @param appId   UUID v7 of the container.
   * @param request the upload request body (fileName required).
   * @return a {@code Response} carrying the presigned upload URL, or
   *         {@link Optional#empty()} when this kind has no presigned upload concept (→ 415).
   */
  default Optional<Response> getUploadUrl(String appId, PresignedUploadRequestIO request) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-5 — optionally register a file uploaded via presigned
   * PUT. This is the converged home for the commit endpoint behind the generic
   * {@code POST /v2/containers/{appId}/upload-url/commit} route, replacing the
   * {@code FileContainerPresignedUrlRest#commitUpload} method.
   *
   * <p>Default returns {@link Optional#empty()} (→ 415) for kinds that don't
   * support presigned uploads (timeseries, structured-data, hdf). The file kind
   * handler overrides this to call {@code FileContainerService.commitUpload(...)}.
   *
   * @param appId  UUID v7 of the container.
   * @param commit the commit body (oid + fileName required).
   * @return a {@code Response} carrying the created ShepardFile, or
   *         {@link Optional#empty()} when this kind has no presigned upload concept (→ 415).
   */
  default Optional<Response> commitUpload(String appId, UploadCommitIO commit) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-5 — optionally obtain a presigned GET URL to download
   * a file directly from the active storage backend (S3). This is the converged home
   * for the download-url endpoint behind the generic
   * {@code GET /v2/containers/{appId}/files/{oid}/download-url} route, replacing
   * the {@code FileContainerPresignedUrlRest#getDownloadUrl} method.
   *
   * <p>Default returns {@link Optional#empty()} (→ 415) for kinds that don't
   * support presigned downloads (timeseries, structured-data, hdf). The file kind
   * handler overrides this to call {@code FileContainerService.presignedDownloadUrl(...)}.
   *
   * @param appId UUID v7 of the container.
   * @param oid   object id (UUID) of the file within the container.
   * @return a {@code Response} carrying the presigned download URL, or
   *         {@link Optional#empty()} when this kind has no presigned download concept (→ 415).
   */
  default Optional<Response> getDownloadUrl(String appId, String oid) {
    return Optional.empty();
  }

  /**
   * APISIMP-OID-PATHPARAM-REPLACE slice 2 — resolve file by stable {@code fileAppId}
   * (UUID v7) and return a PNG thumbnail. Looks up the {@link de.dlr.shepard.data.file.entities.ShepardFile}
   * whose {@code appId} equals {@code fileAppId}, then delegates to
   * {@link #getThumbnail(String, String, Integer)} with the resolved MongoDB OID.
   *
   * <p>Default returns {@link Optional#empty()} (→ 415). Only the file kind handler
   * overrides this; other kinds don't have file payloads.
   *
   * @param appId     UUID v7 of the container.
   * @param fileAppId stable UUID v7 of the file (from {@code ShepardFile.fileAppId}).
   * @param sizeParam requested thumbnail size in pixels.
   * @return a {@code Response} carrying the PNG bytes, or {@link Optional#empty()} (→ 415).
   */
  default Optional<Response> getThumbnailByFileAppId(String appId, String fileAppId, Integer sizeParam) {
    return Optional.empty();
  }

  /**
   * APISIMP-OID-PATHPARAM-REPLACE slice 2 — resolve file by stable {@code fileAppId}
   * (UUID v7) and return a presigned GET URL. Looks up the {@link de.dlr.shepard.data.file.entities.ShepardFile}
   * whose {@code appId} equals {@code fileAppId}, then delegates to
   * {@link #getDownloadUrl(String, String)} with the resolved MongoDB OID.
   *
   * <p>Default returns {@link Optional#empty()} (→ 415). Only the file kind handler
   * overrides this.
   *
   * @param appId     UUID v7 of the container.
   * @param fileAppId stable UUID v7 of the file (from {@code ShepardFile.fileAppId}).
   * @return a {@code Response} carrying the presigned download URL, or {@link Optional#empty()} (→ 415).
   */
  default Optional<Response> getDownloadUrlByFileAppId(String appId, String fileAppId) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — COPY-protocol ingest for a single channel.
   * Returns {@code true} if the ingest was handled (→ 204), {@code false} when
   * this kind has no channels (→ 415).
   *
   * @param containerAppId appId of the container.
   * @param shepardId      UUID of the channel.
   * @param body           the ingest payload.
   * @return true when the ingest was performed; false when unsupported.
   */
  default boolean ingestChannelData(
      String containerAppId, UUID shepardId,
      de.dlr.shepard.v2.timeseriescontainer.io.CopyIngestRequestIO body) {
    return false;
  }

  // ── APISIMP-CONT-NS-COLLAPSE-3: chart-view endpoints ───────────────────────

  /**
   * APISIMP-CONT-NS-COLLAPSE-3 — optionally return the persisted chart-view
   * configuration for the container at {@code appId}. Only timeseries containers
   * carry a chart-view; other kinds return {@link Optional#empty()} (→ 415).
   *
   * <p>The dispatching resource has already gated Read on the container before
   * calling this method.
   *
   * @param appId UUID v7 of the container.
   * @return the chart-view IO, or empty when this kind has no chart-view (→ 415).
   */
  default Optional<TimeseriesContainerChartViewIO> getChartView(String appId) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-3 — optionally apply a merge-patch to the
   * chart-view configuration for the container at {@code appId}. Only timeseries
   * containers carry a chart-view; other kinds return {@link Optional#empty()} (→ 415).
   *
   * <p>The dispatching resource has already gated Write on the container before
   * calling this method.
   *
   * @param appId UUID v7 of the container.
   * @param patch the merge-patch body (RFC 7396 semantics on {@code selectedChannels}).
   * @return the updated chart-view IO, or empty when this kind has no chart-view (→ 415).
   */
  default Optional<TimeseriesContainerChartViewIO> patchChartView(
      String appId, TimeseriesContainerChartViewIO patch) {
    return Optional.empty();
  }

  // ── APISIMP-CONT-NS-COLLAPSE-4: live-window + channel-annotations + temporal-annotations ──

  /**
   * APISIMP-CONT-NS-COLLAPSE-4 — live-window endpoint behind the unified
   * {@code GET /v2/containers/{appId}/channels/live-window} route.
   *
   * <p>Default returns {@link Optional#empty()} — kinds without a timeseries channel
   * concept (file, structured-data) leave the dispatcher to answer 415.
   * The timeseries handler overrides this to return live windowed data.
   *
   * @param appId             UUID v7 of the container.
   * @param shepardId         optional channel identity (preferred, TS-IDc).
   * @param measurement       optional 5-tuple field.
   * @param device            optional 5-tuple field.
   * @param location          optional 5-tuple field.
   * @param symbolicName      optional 5-tuple field.
   * @param field             optional 5-tuple field.
   * @param windowSeconds     window size in seconds (1–3600).
   * @param withBoundaryPoints whether to add interpolated boundary points.
   * @return the response, or empty when this kind has no live-window concept (→ 415).
   */
  default Optional<Response> getLiveWindow(
      String appId, UUID shepardId, String measurement, String device,
      String location, String symbolicName, String field,
      int windowSeconds, boolean withBoundaryPoints) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-4 / APISIMP-CONTAINER-CHANNEL-ANNOTATIONS-UNCAPPED —
   * list channel annotations behind the unified
   * {@code GET /v2/containers/{appId}/channels/{channelShepardId}/annotations} route.
   *
   * @param appId            UUID v7 of the container.
   * @param channelShepardId UUID v7 of the timeseries channel.
   * @param page             zero-based page index (default 0).
   * @param pageSize         items per page, capped at 500 (default 200).
   * @return the paged response, or empty when this kind has no channel-annotation concept (→ 415).
   */
  default Optional<Response> listChannelAnnotations(
      String appId, String channelShepardId, int page, int pageSize) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-4 — create channel annotation behind the unified
   * {@code POST /v2/containers/{appId}/channels/{channelShepardId}/annotations} route.
   *
   * @param appId            UUID v7 of the container.
   * @param channelShepardId UUID v7 of the timeseries channel.
   * @param body             the annotation to create.
   * @return the response, or empty when this kind has no channel-annotation concept (→ 415).
   */
  default Optional<Response> createChannelAnnotation(
      String appId, String channelShepardId, SemanticAnnotationIO body) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-4 — delete channel annotation behind the unified
   * {@code DELETE /v2/containers/{appId}/channels/{channelShepardId}/annotations/{annotationAppId}} route.
   *
   * @param appId             UUID v7 of the container.
   * @param channelShepardId  UUID v7 of the timeseries channel.
   * @param annotationAppId   UUID v7 of the annotation to delete.
   * @return the response, or empty when this kind has no channel-annotation concept (→ 415).
   */
  default Optional<Response> deleteChannelAnnotation(
      String appId, String channelShepardId, String annotationAppId) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-4 / APISIMP-CONTAINER-TEMPORAL-ANNOTATIONS-UNCAPPED —
   * list temporal annotations behind the unified
   * {@code GET /v2/containers/{appId}/temporal-annotations} route.
   *
   * @param appId    UUID v7 of the container.
   * @param page     zero-based page index (default 0).
   * @param pageSize items per page, capped at 500 (default 200).
   * @return the paged response, or empty when this kind has no temporal-annotation concept (→ 415).
   */
  default Optional<Response> listTemporalAnnotations(String appId, int page, int pageSize) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-4 — create temporal annotation behind the unified
   * {@code POST /v2/containers/{appId}/temporal-annotations} route.
   *
   * @param appId UUID v7 of the container.
   * @param body  the annotation to create.
   * @return the response, or empty when this kind has no temporal-annotation concept (→ 415).
   */
  default Optional<Response> createTemporalAnnotation(String appId, TimeseriesAnnotationIO body) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-4 — get a single temporal annotation behind the unified
   * {@code GET /v2/containers/{appId}/temporal-annotations/{annotationAppId}} route.
   *
   * @param appId           UUID v7 of the container.
   * @param annotationAppId UUID v7 of the annotation.
   * @return the response, or empty when this kind has no temporal-annotation concept (→ 415).
   */
  default Optional<Response> getTemporalAnnotation(String appId, String annotationAppId) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-4 — update a temporal annotation behind the unified
   * {@code PATCH /v2/containers/{appId}/temporal-annotations/{annotationAppId}} route.
   *
   * @param appId           UUID v7 of the container.
   * @param annotationAppId UUID v7 of the annotation.
   * @param body            the merge-patch body.
   * @return the response, or empty when this kind has no temporal-annotation concept (→ 415).
   */
  default Optional<Response> updateTemporalAnnotation(
      String appId, String annotationAppId, TimeseriesAnnotationIO body) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-4 — delete a temporal annotation behind the unified
   * {@code DELETE /v2/containers/{appId}/temporal-annotations/{annotationAppId}} route.
   *
   * @param appId           UUID v7 of the container.
   * @param annotationAppId UUID v7 of the annotation.
   * @return the response, or empty when this kind has no temporal-annotation concept (→ 415).
   */
  default Optional<Response> deleteTemporalAnnotation(String appId, String annotationAppId) {
    return Optional.empty();
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-6 — return the {@code appId} strings of the
   * distinct DataObjects that link to the container at {@code appId} via their
   * references. Used by the unified {@code DELETE /v2/containers/{appId}}
   * safe-delete check (returns 409 when active references exist and
   * {@code ?force} is not set).
   *
   * <p>Default returns {@link Optional#empty()} — a kind that does not model
   * linked DataObjects leaves the dispatcher to skip the conflict check and
   * delete immediately. All three core kinds override this.
   *
   * @param appId UUID v7 of the container.
   * @return the linked DataObject appIds (may be empty), or
   *         {@link Optional#empty()} when this kind has no
   *         linked-DataObject concept.
   */
  default Optional<List<String>> findLinkedDataObjectAppIds(String appId) {
    return Optional.empty();
  }
}
