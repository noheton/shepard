package de.dlr.shepard.storage.gridfs;

import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileService;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StorageNotFoundException;
import de.dlr.shepard.storage.StoragePutRequest;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

/**
 * FS1a default {@link FileStorage} adapter — wraps the legacy
 * {@link FileService} GridFS implementation behind the SPI. Lives
 * in core (not in a plugin module) per {@code CLAUDE.md}
 * plugin-first heuristic exception "default that ships with stock
 * shepard, no external dependencies".
 *
 * <p>Locator shape: {@code "<containerMongoId>:<fileOid>"} — the
 * pair {@link FileService} already uses internally. Keeping the
 * shape compatible with the existing bookkeeping means FS1a is a
 * pure refactor: no data shape changes in MongoDB, the V34
 * migration only stamps {@code providerId="gridfs"} on existing
 * Neo4j {@code :ShepardFile} rows.
 *
 * <p><strong>FS1a scope.</strong> This adapter delegates to the
 * existing {@code FileService} methods rather than replacing them.
 * The longer-term refactor (FileService becomes a thin facade that
 * always goes through the registry) is deferred to FS1b — when the
 * second adapter exists, the contract shape stabilises and the
 * refactor is cheap. Today the registry is the new boundary every
 * future caller compiles against, while {@code FileService} stays
 * the actual GridFS implementation.
 *
 * <p>{@link #isEnabled()} returns the configured-MongoDB-up signal —
 * actually checking {@code MongoDatabase} reachability at every
 * call would burn round-trips, so the bean considers itself enabled
 * whenever its {@code @PostConstruct} discovered the
 * {@code mongoDatabase} bean (the upstream {@code MongoClientWrapper}
 * fails fast on Mongo-down at startup anyway, so a startup-time
 * check is sufficient).
 */
@ApplicationScoped
public class GridFsFileStorage implements FileStorage {

  /** Stable id; matches {@code shepard.storage.provider=gridfs}. */
  public static final String ID = "gridfs";

  /**
   * Locator separator between {@code containerMongoId} and
   * {@code fileOid}. The colon is safe — neither side of the pair
   * contains it (the container side is the
   * {@code "FileContainer<UUID>"} string, the file side is a Mongo
   * {@code ObjectId} hex string).
   */
  public static final char LOCATOR_SEPARATOR = ':';

  @Inject
  FileService fileService;

  private volatile boolean enabled;

  @PostConstruct
  void init() {
    // The actual Mongo health check lives in the upstream
    // MongoClientWrapper + the A1b per-DB health scheduler. By the
    // time CDI constructs this bean, the Mongo connection has
    // either succeeded (we're enabled) or the entire app would have
    // failed startup. So a simple "I'm constructed" is the readiness
    // signal — refinements (e.g. honour `shepard.storage.gridfs.enabled`)
    // queue for FS1b alongside the S3 adapter's runtime knobs.
    this.enabled = fileService != null;
    Log.infof("GridFsFileStorage: registered (id=%s, enabled=%s)", ID, enabled);
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public StorageLocator put(StoragePutRequest request) throws StorageException {
    try {
      ShepardFile created = fileService.createFile(request.container(), request.fileName(), request.bytes());
      return new StorageLocator(ID, request.container() + LOCATOR_SEPARATOR + created.getOid());
    } catch (NotFoundException nfe) {
      // FileService throws JAX-RS NotFoundException when the container
      // Mongo collection isn't reachable — surface it as the SPI's
      // storage-tier not-found shape so the caller (FileContainerService)
      // can either rethrow with its own envelope or map cleanly.
      throw new StorageNotFoundException(
        "GridFS container '" + request.container() + "' not found: " + nfe.getMessage(),
        nfe
      );
    } catch (RuntimeException re) {
      // Catch-all for Mongo driver / MD5 algorithm / digest stream
      // failures. The legacy FileService throws InternalServerErrorException
      // on a missing MD5 algorithm; everything else is a Mongo-side
      // RuntimeException. Both become a generic StorageException so
      // the REST layer renders the same 500 envelope.
      throw new StorageException("GridFS put failed: " + re.getMessage(), re);
    }
  }

  @Override
  public StorageGetResponse get(StorageLocator locator) throws StorageException {
    requireGridFsLocator(locator);
    var parts = splitLocator(locator);
    try {
      NamedInputStream named = fileService.getPayload(parts.containerMongoId(), parts.fileOid());
      // Legacy GridFS path doesn't record content-type — that
      // bookkeeping arrives with FS1b's S3 adapter (S3 has a
      // Content-Type header), so it stays null here.
      return new StorageGetResponse(ID, named.getName(), null, named.getSize(), named.getInputStream());
    } catch (NotFoundException nfe) {
      throw new StorageNotFoundException(
        "GridFS payload at '" + locator.locator() + "' not found: " + nfe.getMessage(),
        nfe
      );
    } catch (RuntimeException re) {
      throw new StorageException("GridFS get failed: " + re.getMessage(), re);
    }
  }

  @Override
  public void delete(StorageLocator locator) throws StorageException {
    requireGridFsLocator(locator);
    var parts = splitLocator(locator);
    try {
      fileService.deleteFile(parts.containerMongoId(), parts.fileOid());
    } catch (NotFoundException nfe) {
      // Idempotent contract: a missing key must not throw. The
      // legacy FileService throws JAX-RS NotFound on both
      // container-missing and file-missing; we swallow the file
      // case so a retry-after-partial-failure is safe. Container
      // misses we still raise because the caller's bookkeeping is
      // ahead of the storage tier — a meaningful inconsistency.
      String msg = nfe.getMessage();
      if (msg != null && msg.startsWith("Could not find and delete file")) {
        Log.debugf("GridFsFileStorage: delete on missing locator '%s' — treated as no-op (idempotent)", locator.locator());
        return;
      }
      throw new StorageNotFoundException(
        "GridFS container for locator '" + locator.locator() + "' not found: " + msg,
        nfe
      );
    } catch (RuntimeException re) {
      throw new StorageException("GridFS delete failed: " + re.getMessage(), re);
    }
  }

  private static void requireGridFsLocator(StorageLocator locator) throws StorageException {
    if (locator == null) {
      throw new StorageException("locator must not be null");
    }
    if (!ID.equals(locator.providerId())) {
      throw new StorageException(
        "GridFsFileStorage received locator for provider '" + locator.providerId() +
        "' — refusing to dispatch. The FileStorageRegistry should route via providerId."
      );
    }
  }

  static LocatorParts splitLocator(StorageLocator locator) throws StorageException {
    String raw = locator.locator();
    int sep = raw.indexOf(LOCATOR_SEPARATOR);
    if (sep <= 0 || sep == raw.length() - 1) {
      throw new StorageException(
        "Malformed GridFS locator '" + raw + "' — expected '<containerMongoId>:<fileOid>'"
      );
    }
    return new LocatorParts(raw.substring(0, sep), raw.substring(sep + 1));
  }

  /** GridFS locator components split out from the opaque string. */
  record LocatorParts(String containerMongoId, String fileOid) {}
}
