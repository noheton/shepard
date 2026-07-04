package de.dlr.shepard.v2.svdx.services;

/**
 * V2CONV-A7 — plain input shape for {@link SvdxCsvIngestionService#ingest}.
 *
 * <p>Replaces the former {@code SvdxIngestRequestIO} REST body. When the bespoke
 * {@code POST /v2/svdx/ingest} endpoint was dissolved onto the generic
 * MAPPING_RECIPE / {@code TransformExecutor} seam (aidocs/platform/191 Tier-3),
 * the service stopped being a REST handler collaborator and became a
 * {@link de.dlr.shepard.v2.svdx.transform.SvdxCsvTransformExecutor} collaborator.
 * Its input is therefore a SPI-neutral record — no Jackson / MicroProfile / Bean
 * Validation annotations — exactly mirroring how the KRL dissolution (V2CONV-B5)
 * gave its service a {@code KrlTrajectoryParams} record in place of the deleted
 * {@code KrlInterpretRequestIO}.
 *
 * @param svdxFileAppId    appId (UUID v7) of the {@code .svdx} singleton FileReference — REQUIRED
 * @param csvFileAppId     appId (UUID v7) of the TwinCAT-Scope-Export {@code .csv}
 *                         singleton FileReference — REQUIRED
 * @param dataObjectAppId  appId (UUID v7) of the parent DataObject the derived
 *                         TimeseriesReference attaches under — REQUIRED. Both file
 *                         singletons must be attached to this DataObject.
 * @param tsContainerAppId appId of an existing TimeseriesContainer to ingest into;
 *                         when null/blank a new container is minted in the same Collection
 * @param referenceName    display name for the derived TimeseriesReference; when
 *                         null/blank a deterministic {@code svdx-ingest:<svdxId>+<csvId>}
 *                         name is used (the idempotency key)
 */
public record SvdxCsvIngestParams(
    String svdxFileAppId,
    String csvFileAppId,
    String dataObjectAppId,
    String tsContainerAppId,
    String referenceName) {}
