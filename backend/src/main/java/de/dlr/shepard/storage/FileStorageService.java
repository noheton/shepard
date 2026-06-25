package de.dlr.shepard.storage;

import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileService;
import de.dlr.shepard.storage.gridfs.GridFsFileStorage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;

/**
 * STORAGE-SPI-UNIFY-1 — the single in-tree choke point through which
 * every single-file byte write / read / delete routes when the caller
 * holds a {@link ShepardFile}-shaped payload but is <em>not</em> the
 * {@code FileContainerService} (which owns its own registry-routed
 * path for the upstream-byte-compat {@code /shepard/api/...fileContainers}
 * surface).
 *
 * <p><strong>Why this exists.</strong> Before STORAGE-SPI-UNIFY-1 the
 * singleton-FileReference path
 * ({@link de.dlr.shepard.context.references.file.services.SingletonFileReferenceService})
 * and the bundle-upload path
 * ({@code de.dlr.shepard.v2.bundle.resources.FileBundleReferenceRest})
 * called {@link FileService#createFile} <em>directly</em>, hardcoding
 * the GridFS write and bypassing {@link FileStorageRegistry#activeStorage()}.
 * That made {@code shepard.storage.provider=s3} a no-op for those two
 * paths — file bytes still landed in MongoDB GridFS even with an S3
 * backend active. This service is the "no magic routes" fix: callers
 * ask <em>it</em> to store/read/delete, and it consults the registry to
 * pick the active (write) or per-row (read/delete) adapter.
 *
 * <p><strong>Relationship to {@link FileService}.</strong> {@link FileService}
 * stays the GridFS <em>implementation detail</em> — it is the body of the
 * sanctioned {@link GridFsFileStorage} adapter. This service NEVER touches
 * a storage substrate directly; for the GridFS provider it delegates to
 * {@link FileService} (the legitimate adapter implementation), for every
 * other provider it issues {@link FileStorage#put}/{@code get}/{@code delete}
 * through the SPI. The decision of <em>which</em> provider is always made
 * via {@link FileStorageRegistry}, never hardcoded.
 *
 * <p><strong>Read / delete route per-row.</strong> A file's bytes live
 * under whatever provider stored them ({@link ShepardFile#getProviderId()},
 * backfilled to {@code "gridfs"} by V79 for pre-FS1a rows). Reads and
 * deletes resolve that provider via {@link #adapterForProvider} so
 * existing GridFS content keeps resolving even after an operator flips
 * the active provider to {@code s3} — the millions of already-stored
 * MFFD files are never assumed to live under the active provider.
 */
@RequestScoped
public class FileStorageService {

  @Inject
  FileStorageRegistry registry;

  @Inject
  FileService fileService;

  /** Constructor for CDI. */
  public FileStorageService() {}

  /**
   * Visible for testing — wires the collaborators without CDI so a
   * plain JUnit test can drive the routing logic with mocks.
   */
  public FileStorageService(FileStorageRegistry registry, FileService fileService) {
    this.registry = registry;
    this.fileService = fileService;
  }

  /**
   * Store a single file's bytes into {@code container} through the
   * <em>active</em> storage adapter and return the persisted
   * {@link ShepardFile} (oid / md5 / size as the adapter records them),
   * with {@link ShepardFile#getProviderId()} stamped to the active
   * adapter id so subsequent reads / deletes route back to it.
   *
   * <p>GridFS path: delegates to {@link FileService#createFile} (the
   * sanctioned adapter implementation) so the GridFS bookkeeping +
   * md5 + size are recorded exactly as before. Non-GridFS path: issues
   * {@link FileStorage#put} and builds a metadata-only {@link ShepardFile}
   * keyed on the locator's object id.
   *
   * @param container    the storage container / namespace identifier
   *                     (a {@code FileContainer.mongoId}, the shared
   *                     {@code _shepard_files} namespace, …). Never blank.
   * @param fileName     original filename; used for Content-Disposition.
   * @param inputStream  the payload bytes. Consumed by the adapter.
   * @param declaredSize caller-declared size in bytes; {@code <= 0} skips
   *                     the GridFS size-cap check.
   * @return the persisted {@link ShepardFile} with providerId stamped.
   * @throws StorageNotInstalledException when no storage adapter is active.
   * @throws ServiceUnavailableException  when the active provider does not
   *                                      support direct (non-presigned)
   *                                      upload (e.g. S3 — use the
   *                                      presigned-URL endpoints instead).
   */
  public ShepardFile storeFile(String container, String fileName, InputStream inputStream, long declaredSize) {
    FileStorage adapter = registry.requireActive();
    ShepardFile result;
    if (GridFsFileStorage.ID.equals(adapter.id())) {
      // GridFS is the in-core default; FileService is its sanctioned
      // implementation. Routing the decision through requireActive()
      // keeps this honest — the moment the operator flips the active
      // provider, this branch stops being taken.
      result = fileService.createFile(container, fileName, inputStream, declaredSize);
    } else {
      // STORAGE-SPI-UNIFY-PUT: non-GridFS adapters (S3, future
      // Garage-direct) stream server-side through the SPI's put().
      // The adapter (e.g. S3FileStorage) reads the bytes via
      // RequestBody.fromInputStream and returns a StorageLocator
      // "<container>/<key>". We compute the md5 while streaming so the
      // FB1a md5 bookkeeping is populated for object-store rows too,
      // and we derive the oid from the locator so the read path
      // (getPayload → buildLocator) round-trips to the same key.
      result = storeViaSpi(adapter, container, fileName, inputStream, declaredSize);
    }
    if (result != null) {
      result.setProviderId(adapter.id());
      // NEO-AUDIT-002 write-path guard — providerId must be set before
      // any caller persists the entity.
      Objects.requireNonNull(
        result.getProviderId(),
        "ShepardFile.providerId must be set before persistence (NEO-AUDIT-002)"
      );
    }
    return result;
  }

  /**
   * STORAGE-SPI-UNIFY-PUT / STORAGE-SPI-PUT-SIZEFIX — server-side store
   * through a non-GridFS {@link FileStorage} adapter's {@link FileStorage#put}.
   *
   * <p><strong>Why the temp-file spool.</strong> The {@code declaredSize}
   * the caller forwards is, on several paths, derived from the inbound
   * {@code Content-Length} HTTP header (the singleton-content PUT
   * {@code PUT /v2/references/{appId}/content}) rather than from the true
   * count of bytes the entity {@link InputStream} yields. When those two
   * disagree — chunked transfer-encoding, a reverse proxy re-framing the
   * body, gzip on the wire, or RESTEasy handing back a partially-consumed
   * stream — passing the wrong size to {@code RequestBody.fromInputStream
   * (stream, size)} makes S3 {@code PutObject} abort with
   * <em>"The request content has fewer bytes than the specified
   * content-length"</em> (the live 500 on an 11-byte upload). To make the
   * SPI put robust to a wrong/zero {@code declaredSize}, we spool the
   * (MD5-digesting) stream to a temp file, then hand the adapter the
   * <em>actual</em> file length — so the size always matches the bytes.
   *
   * <p>A temp file (not a heap buffer) keeps this safe for large uploads:
   * an importer may stream a ~1 GB svdx without exhausting the heap. The
   * MD5 is computed during the copy (over the real bytes), and the temp
   * file is always deleted in a {@code finally}.
   *
   * <p>Derives the persisted {@code oid} from the returned locator's key
   * segment so the read path ({@link #getPayload} → {@link #buildLocator})
   * reconstructs the exact same locator and finds the bytes.
   *
   * @return a metadata-only {@link ShepardFile} carrying oid / filename /
   *         md5 / fileSize. The caller stamps {@code providerId}.
   */
  ShepardFile storeViaSpi(FileStorage adapter, String container, String fileName, InputStream inputStream, long declaredSize) {
    MessageDigest md5Digest;
    try {
      md5Digest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      // MD5 is a JDK-guaranteed algorithm; this never fires.
      throw new ServiceUnavailableException("MD5 unavailable for storage md5 bookkeeping: " + e.getMessage());
    }

    Path tmp = null;
    try {
      // Spool to a temp file while digesting, so we never trust a
      // possibly-wrong declaredSize for the PutObject content-length.
      tmp = Files.createTempFile("shepard-spi-put-", ".bin");
      long actualSize;
      try (DigestInputStream digesting = new DigestInputStream(inputStream, md5Digest);
           OutputStream out = Files.newOutputStream(tmp)) {
        digesting.transferTo(out);
        actualSize = Files.size(tmp);
      }
      String md5 = HexFormat.of().formatHex(md5Digest.digest());

      StorageLocator locator;
      // Hand the adapter a fresh stream over the temp file plus its TRUE
      // length — this is the size that exactly matches the bytes, so the
      // S3 RequestBody.fromInputStream(stream, size) path never mismatches.
      try (InputStream fileStream = new BufferedInputStream(Files.newInputStream(tmp))) {
        StoragePutRequest put = new StoragePutRequest(container, fileName, null, fileStream, actualSize, null);
        locator = adapter.put(put);
      } catch (StorageQuotaExceededException qee) {
        throw new ServiceUnavailableException(
          "Storage provider '" + adapter.id() + "' quota exceeded: " + qee.getMessage());
      } catch (StorageException se) {
        Log.errorf("FileStorageService.storeFile: SPI put failed on container=%s adapter=%s — %s",
          container, adapter.id(), se.getMessage());
        throw new ServiceUnavailableException(
          "Storage provider '" + adapter.id() + "' failed to store file: " + se.getMessage());
      }

      String oid = oidFromLocator(container, locator.locator());
      ShepardFile file = new ShepardFile(oid, new Date(), fileName, md5);
      file.setFileSize(actualSize);
      return file;
    } catch (IOException ioe) {
      throw new ServiceUnavailableException(
        "Storage provider '" + adapter.id() + "' failed to spool upload bytes: " + ioe.getMessage());
    } finally {
      if (tmp != null) {
        try {
          Files.deleteIfExists(tmp);
        } catch (IOException cleanup) {
          Log.warnf("FileStorageService.storeViaSpi: could not delete temp file %s — %s", tmp, cleanup.getMessage());
        }
      }
    }
  }

  /**
   * Extract the persisted {@code oid} (the read-path key segment) from a
   * non-GridFS locator of the form {@code "<container>/<key>"}. The read
   * path rebuilds the locator as {@code container + "/" + oid}, so the
   * oid must be exactly the trailing key after {@code "<container>/"}.
   * Falls back to the substring after the last {@code '/'} if the
   * adapter chose a different container prefix.
   */
  static String oidFromLocator(String container, String locator) {
    String prefix = container + "/";
    if (locator.startsWith(prefix)) {
      return locator.substring(prefix.length());
    }
    int slash = locator.lastIndexOf('/');
    return slash >= 0 && slash < locator.length() - 1 ? locator.substring(slash + 1) : locator;
  }

  /**
   * Read a single file's bytes, routing to the adapter that <em>stored</em>
   * it (per {@code file.providerId}), not the currently-active provider —
   * so existing GridFS content resolves after a provider flip.
   *
   * @param container the storage container / namespace identifier.
   * @param file      the {@link ShepardFile} whose bytes to fetch; its
   *                  {@code providerId} selects the adapter (null /
   *                  blank falls back to {@code gridfs}).
   * @return a {@link NamedInputStream} over the bytes.
   * @throws NotFoundException when the payload is missing.
   * @throws ServiceUnavailableException on a storage-tier failure.
   */
  public NamedInputStream getPayload(String container, ShepardFile file) {
    String oid = file != null ? file.getOid() : null;
    if (oid == null) {
      throw new NotFoundException("File has no object id");
    }
    String providerId = effectiveProviderId(file);
    FileStorage adapter = adapterForProvider(providerId);
    StorageLocator locator = buildLocator(providerId, container, oid);
    try {
      StorageGetResponse resp = adapter.get(locator);
      return new NamedInputStream(oid, resp.stream(), resp.fileName(), resp.sizeBytes());
    } catch (StorageNotFoundException snfe) {
      throw new NotFoundException(snfe.getMessage());
    } catch (StorageException se) {
      Log.errorf("FileStorageService.getPayload: storage failure on container=%s oid=%s — %s",
        container, oid, se.getMessage());
      throw new ServiceUnavailableException("Storage provider '" + providerId + "' failed: " + se.getMessage());
    }
  }

  /**
   * Delete a single file's bytes, routing to the adapter that stored
   * it. Idempotent — a missing payload is a no-op.
   *
   * @param container the storage container / namespace identifier.
   * @param file      the {@link ShepardFile} to delete; its
   *                  {@code providerId} selects the adapter.
   * @throws ServiceUnavailableException on a non-404 storage-tier failure.
   */
  public void deleteFile(String container, ShepardFile file) {
    String oid = file != null ? file.getOid() : null;
    if (oid == null) {
      return;
    }
    String providerId = effectiveProviderId(file);
    FileStorage adapter = adapterForProvider(providerId);
    StorageLocator locator = buildLocator(providerId, container, oid);
    try {
      adapter.delete(locator);
    } catch (StorageNotFoundException snfe) {
      // delete() is idempotent at the adapter; defensive swallow here.
      Log.debugf("FileStorageService.deleteFile: 404 on container=%s oid=%s — treated as no-op", container, oid);
    } catch (StorageException se) {
      Log.errorf("FileStorageService.deleteFile: storage failure on container=%s oid=%s — %s",
        container, oid, se.getMessage());
      throw new ServiceUnavailableException("Storage provider '" + providerId + "' failed: " + se.getMessage());
    }
  }

  /**
   * Resolve the {@link FileStorage} adapter for a stored row's
   * provider id. Falls back to the active adapter when the row's
   * provider is not installed / disabled (the 503-on-demand posture —
   * "operator removed the adapter but rows still reference it").
   */
  private FileStorage adapterForProvider(String providerId) {
    for (FileStorage s : registry.list()) {
      if (providerId.equals(s.id())) {
        if (s.isEnabled()) return s;
        Log.warnf("FileStorageService: storage adapter '%s' is registered but disabled — falling back to active provider", providerId);
        break;
      }
    }
    return registry.requireActive();
  }

  /**
   * Build the opaque locator value for a stored file row. GridFS uses
   * the {@code ":"} separator; all other adapters use {@code "/"} to
   * match their {@code container/uuid} key format.
   */
  static StorageLocator buildLocator(String providerId, String container, String oid) {
    if (GridFsFileStorage.ID.equals(providerId)) {
      return new StorageLocator(providerId, container + GridFsFileStorage.LOCATOR_SEPARATOR + oid);
    }
    return new StorageLocator(providerId, container + "/" + oid);
  }

  /** {@code file.providerId} when present, else the {@code gridfs} default (V79 backfill / fixtures). */
  static String effectiveProviderId(ShepardFile file) {
    if (file == null) return GridFsFileStorage.ID;
    String pid = file.getProviderId();
    return (pid == null || pid.isBlank()) ? GridFsFileStorage.ID : pid;
  }
}
