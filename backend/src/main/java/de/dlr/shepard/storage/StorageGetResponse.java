package de.dlr.shepard.storage;

import java.io.InputStream;

/**
 * Output of a successful {@link FileStorage#get(StorageLocator)} call.
 *
 * <p>FS1a baseline shape per {@code aidocs/45 §3.2}. The caller
 * (typically {@code FileContainerService} / {@code FileRest})
 * forwards the {@link #stream} to the HTTP layer as the response
 * body, with the {@link #fileName} populating
 * {@code Content-Disposition} and {@link #sizeBytes} populating
 * {@code Content-Length}.
 *
 * <p><strong>Stream ownership.</strong> The caller is responsible
 * for closing the stream — JAX-RS's
 * {@code StreamingOutput.write(OutputStream)} contract does this
 * when the response body is fully written; the legacy upstream
 * surface relied on the same convention.
 *
 * @param providerId   {@link FileStorage#id()} of the adapter that
 *                     produced the response. Stamped for diagnostic
 *                     visibility — clients never see this field.
 * @param fileName     human-readable filename. Never null, never
 *                     blank.
 * @param contentType  MIME type. Nullable — see
 *                     {@link StoragePutRequest#contentType()}.
 * @param sizeBytes    payload size in bytes. Nullable — the legacy
 *                     GridFS path can return null for very-old rows
 *                     that pre-date FB1a's {@code fileSize}
 *                     bookkeeping.
 * @param stream       the payload bytes. May be null only when an
 *                     adapter returns a head-only response (FS1a
 *                     happy path always returns the stream; this
 *                     loosens the contract for future adapters that
 *                     want to surface metadata-only responses for
 *                     HEAD-style requests without forcing a body).
 */
public record StorageGetResponse(
  String providerId,
  String fileName,
  String contentType,
  Long sizeBytes,
  InputStream stream
) {
  public StorageGetResponse {
    if (providerId == null || providerId.isBlank()) {
      throw new IllegalArgumentException("providerId must not be null/blank");
    }
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("fileName must not be null/blank");
    }
    if (sizeBytes != null && sizeBytes < 0) {
      throw new IllegalArgumentException("sizeBytes must be >= 0 (got " + sizeBytes + ")");
    }
  }
}
