package de.dlr.shepard.storage;

import java.io.InputStream;

/**
 * Input payload for {@link FileStorage#put(StoragePutRequest)} —
 * describes the bytes the caller wants stored. FS1a baseline shape
 * per {@code aidocs/45 §3.2}.
 *
 * <p>Fields kept minimal-by-design: the caller pulls richer
 * payload metadata (createdAt, md5, semantic annotations) from the
 * Neo4j side when the REST layer builds the response, but the
 * {@link FileStorage} interface only needs the wire-shape
 * identifiers + the bytes so an adapter (GridFS today, S3 in FS1b,
 * future Garage-direct / SeaweedFS adapters) can issue the call
 * without re-loading the entity.
 *
 * @param container    container identifier — for GridFS this is the
 *                     {@code FileContainer.mongoId} (i.e. the
 *                     per-FileContainer Mongo collection name);
 *                     for S3 (FS1b) this is the bucket name (or a
 *                     bucket-prefix combination). The adapter
 *                     interprets it; the caller forwards it from
 *                     the entity. Never null, never blank.
 * @param fileName     human-readable filename used for
 *                     {@code Content-Disposition} on download. Never
 *                     null, never blank — the {@code FileContainerService}
 *                     synthesises a {@code shepard-file-<timestamp>}
 *                     name when the upload omits one (legacy upstream
 *                     behaviour).
 * @param contentType  MIME type. Nullable — the legacy GridFS path
 *                     never recorded this; FS1b will start recording
 *                     it for S3's {@code Content-Type} metadata. Old
 *                     rows have {@code null} until they are
 *                     re-uploaded.
 * @param bytes        the payload stream. The adapter consumes this
 *                     and is responsible for closing it after
 *                     reading; on exception, the caller must close
 *                     it. Never null.
 * @param sizeBytes          optional pre-known size of the payload. The
 *                           legacy GridFS path computes this after the
 *                           fact via {@code GridFSFile.getLength()} so this
 *                           can be {@code null}; FS1b's S3 adapter can use
 *                           it to pick multipart vs single-part. Nullable.
 * @param assignedObjectKey  FS1e1 migration hint — when non-null, the S3
 *                           adapter stores the object under
 *                           {@code container/assignedObjectKey} instead of
 *                           generating a fresh UUID. Used by
 *                           {@code FileMigrationService} to preserve the
 *                           original GridFS {@code oid} across a
 *                           gridfs→s3 migration so existing API clients
 *                           keep working (the locator key changes from
 *                           {@code "gridfs"} to {@code "s3"} but the oid
 *                           stays identical). Nullable — normal (non-
 *                           migration) uploads leave this null.
 */
public record StoragePutRequest(
  String container,
  String fileName,
  String contentType,
  InputStream bytes,
  Long sizeBytes,
  String assignedObjectKey
) {
  public StoragePutRequest {
    if (container == null || container.isBlank()) {
      throw new IllegalArgumentException("container must not be null/blank");
    }
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("fileName must not be null/blank");
    }
    if (bytes == null) {
      throw new IllegalArgumentException("bytes must not be null");
    }
    if (sizeBytes != null && sizeBytes < 0) {
      throw new IllegalArgumentException("sizeBytes must be >= 0 (got " + sizeBytes + ")");
    }
  }

  /**
   * Convenience factory for callers that don't yet know the
   * content-type or the size (the legacy upstream upload path).
   * Defaults to {@code null} for both — adapters treat null as
   * "unknown, use sensible defaults".
   */
  public StoragePutRequest(String container, String fileName, InputStream bytes) {
    this(container, fileName, null, bytes, null, null);
  }
}
