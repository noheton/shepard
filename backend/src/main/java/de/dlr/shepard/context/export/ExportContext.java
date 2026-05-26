package de.dlr.shepard.context.export;

/**
 * Immutable context object threaded through the built-in {@link PayloadExportHandler}
 * dispatch. Carries the per-dispatch data that built-in handlers need but that the
 * plugin-facing {@link ExportContributor} signature intentionally does not expose.
 *
 * @param collectionId  shepard id of the owning collection
 * @param dataObjectId  shepard id of the owning data object
 * @param username      the exporting user's username (for credential lookups etc.)
 * @param perPayload    optional per-payload pick (columns / file OIDs / time range);
 *                      {@code null} iff no per-payload selection was provided
 * @param strict        when {@code true}, unknown column / OID references are a 400
 *                      rather than a skipped warning
 */
public record ExportContext(
  long collectionId,
  long dataObjectId,
  String username,
  ExportSelection.PerPayloadSelection perPayload,
  boolean strict
) {}
