package de.dlr.shepard.context.references.file.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.storage.FileStorageService;
import de.dlr.shepard.spi.fileparser.FileParserRegistry;
import de.dlr.shepard.spi.payload.FileKindDetectorRegistry;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * CRUD for the FR1b singleton {@link FileReference} entity
 * (see {@code aidocs/53 §1.8}).
 *
 * <p>Behavioural contract:
 * <ul>
 *   <li>One {@link ShepardFile} per singleton. The cardinality is
 *       enforced at write time — {@link #createSingleton} rejects
 *       multipart bodies that contain anything other than a single
 *       file part.</li>
 *   <li>Bytes live in a shared MongoDB collection named
 *       {@link #SHARED_FILES_NAMESPACE} (a regular GridFS-backed
 *       collection, sharing the existing {@link FileService} CRUD
 *       path used by {@link FileBundleReferenceService}). Singletons
 *       do <strong>not</strong> own per-Reference Mongo collections
 *       — that would cost one empty collection per uploaded PDF, the
 *       exact metadata-DB bloat §1.8.3 calls out.</li>
 *   <li>Permissions enforced at the REST layer
 *       ({@link de.dlr.shepard.v2.file.resources.FileReferenceV2Rest})
 *       against the parent DataObject; this service trusts its
 *       callers, mirroring {@link FileGroupService}'s posture.</li>
 * </ul>
 *
 * <p><strong>Naming.</strong> Class name is
 * {@code SingletonFileReferenceService} (not
 * {@code FileReferenceService}) to keep symmetry with
 * {@link SingletonFileReferenceDAO} and avoid colliding with the
 * pre-FR1a {@code FileReferenceService} class name that survives in
 * older branches' import graphs. The class operates on the
 * {@link FileReference} entity type — the FR1b-reclaimed name for
 * the singleton case.
 */
@RequestScoped
public class SingletonFileReferenceService {

  /**
   * Shared MongoDB collection name for singleton-FileReference bytes.
   * One collection, shared across every singleton in the instance —
   * the {@code shepard-files} namespace cited in {@code aidocs/53
   * §1.8.3}. The leading underscore avoids collisions with the
   * randomly-generated {@code FileContainer<uuid>} names that
   * {@link FileService#createFileContainer()} mints for the per-bundle
   * case.
   */
  public static final String SHARED_FILES_NAMESPACE = "_shepard_files";

  @Inject
  SingletonFileReferenceDAO singletonFileReferenceDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  FileStorageService fileStorageService;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  FileKindDetectorRegistry detectorRegistry;

  @Inject
  FileParserRegistry parserRegistry;

  /**
   * Get a singleton by appId.
   *
   * @param appId the singleton's appId.
   * @return the singleton (with its attached {@link ShepardFile}), or
   *   {@code null} when no row matches.
   */
  public FileReference getByAppId(String appId) {
    return singletonFileReferenceDAO.findByAppId(appId);
  }

  /**
   * List all singletons attached to a given DataObject.
   *
   * @param dataObjectAppId parent DataObject's appId.
   * @return all singletons under that DataObject (may be empty).
   */
  public List<FileReference> listByDataObject(String dataObjectAppId) {
    return singletonFileReferenceDAO.findByDataObjectAppId(dataObjectAppId);
  }

  /**
   * APISIMP-KIND-DISCRIMINATOR Option C — phase 1 of the two-step file create.
   *
   * <p>Creates a metadata-only {@link FileReference} node (no bytes, no GridFS
   * doc) attached to the given DataObject. The caller follows up with
   * {@link #attachContent} ({@code PUT /v2/references/{appId}/content}) to
   * store the binary payload and complete the reference.
   *
   * @param dataObjectAppId parent DataObject's appId. Required.
   * @param name human-readable name for the reference. Required, non-blank.
   * @return the persisted, content-less singleton (file field is null).
   * @throws NotFoundException when no DataObject with that appId exists.
   * @throws BadRequestException when {@code name} is missing/blank.
   */
  public FileReference createSingletonMetadata(String dataObjectAppId, String name) {
    if (name == null || name.isBlank()) {
      throw new BadRequestException("name must not be null or blank");
    }
    DataObject parent = resolveDataObjectByAppId(dataObjectAppId);
    if (parent == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }
    User user = userService.getCurrentUser();

    FileReference singleton = new FileReference();
    singleton.setName(name);
    singleton.setDataObject(parent);
    singleton.setCreatedAt(dateHelper.getDate());
    singleton.setCreatedBy(user);

    FileReference created = singletonFileReferenceDAO.createOrUpdate(singleton);
    created.setShepardId(created.getId());
    created = singletonFileReferenceDAO.createOrUpdate(created);

    Log.debugf(
      "FR1b/phase-1: created metadata-only singleton FileReference appId=%s under DataObject appId=%s",
      created.getAppId(),
      dataObjectAppId
    );
    return created;
  }

  /**
   * APISIMP-KIND-DISCRIMINATOR Option C — phase 2 of the two-step file create.
   *
   * <p>Attaches binary content to the content-less {@link FileReference} node
   * identified by {@code appId}. If the reference already has an attached file
   * (re-upload scenario), the old GridFS blob is deleted and replaced.
   *
   * @param appId the singleton's appId (must exist).
   * @param filename original filename; used for MIME / fileKind detection. Required.
   * @param payload byte stream of the file body. Required, non-null.
   * @param declaredSize caller-declared size in bytes; {@code <= 0} skips size cap.
   * @return the updated singleton with its attached file.
   * @throws NotFoundException when no singleton with that appId exists.
   * @throws BadRequestException when {@code filename} or {@code payload} are missing.
   */
  public FileReference attachContent(String appId, String filename, InputStream payload, long declaredSize) {
    if (filename == null || filename.isBlank()) {
      throw new BadRequestException("filename must not be null or blank");
    }
    if (payload == null) {
      throw new BadRequestException("file payload must not be null");
    }
    FileReference ref = singletonFileReferenceDAO.findByAppId(appId);
    if (ref == null) {
      throw new NotFoundException("No singleton FileReference with appId " + appId);
    }

    ensureSharedNamespace();

    // Replace an existing file blob if this is a re-upload.
    ShepardFile oldFile = ref.getFile();

    boolean anyAccepts = parserRegistry != null && parserRegistry.anyAccepts(null, filename);
    byte[] parseBuf = null;
    ShepardFile saved;
    if (anyAccepts) {
      try {
        parseBuf = payload.readAllBytes();
      } catch (IOException e) {
        Log.warnf(e, "FileParserRegistry: could not buffer payload for '%s'; skipping parse", filename);
      }
      if (parseBuf != null) {
        saved = fileStorageService.storeFile(SHARED_FILES_NAMESPACE, filename, new ByteArrayInputStream(parseBuf), declaredSize);
      } else {
        saved = fileStorageService.storeFile(SHARED_FILES_NAMESPACE, filename, payload, declaredSize);
      }
    } else {
      saved = fileStorageService.storeFile(SHARED_FILES_NAMESPACE, filename, payload, declaredSize);
    }

    // Delete old blob after writing the new one (write-then-delete avoids data loss on failure).
    if (oldFile != null) {
      try {
        fileStorageService.deleteFile(SHARED_FILES_NAMESPACE, oldFile);
      } catch (NotFoundException nfe) {
        Log.warnf("FR1b/attachContent: old stored blob oid=%s already missing for appId=%s", oldFile.getOid(), appId);
      }
    }

    ref.setFile(saved);
    ref.setFileKind(detectFileKind(filename, saved));
    ref.setUpdatedAt(dateHelper.getDate());
    ref.setUpdatedBy(userService.getCurrentUser());
    FileReference updated = singletonFileReferenceDAO.createOrUpdate(ref);

    Log.debugf(
      "FR1b/phase-2: attached content to singleton FileReference appId=%s (oid=%s)",
      appId,
      saved.getOid()
    );

    if (parseBuf != null) {
      final byte[] buf = parseBuf;
      DataObject parent = ref.getDataObject();
      String dataObjectAppId = parent != null ? parent.getAppId() : null;
      try {
        parserRegistry.runAll(buf, filename, appId, dataObjectAppId);
      } catch (Exception e) {
        Log.warnf(e, "FileParserRegistry: secondary write failed for filename='%s' appId=%s", filename, appId);
      }
    }

    return updated;
  }

  /**
   * Create a new singleton attached to a parent DataObject with an explicit size cap check.
   *
   * <p>MONGO-AUDIT-2026-05-24-012: when {@code declaredSize > 0} and exceeds
   * {@code shepard.mongo.file.max-bytes}, an {@link de.dlr.shepard.common.exceptions.InvalidRequestException}
   * is thrown before any GridFS write occurs.
   *
   * @param dataObjectAppId parent DataObject's appId. Required.
   * @param name human-readable name for the Reference. Required, non-blank.
   * @param filename original filename of the upload. Required, non-blank.
   * @param payload byte stream of the file body. Required, non-null.
   * @param declaredSize caller-declared file size in bytes; {@code <= 0} skips the size cap check.
   * @return the persisted singleton with its attached file.
   * @throws NotFoundException when no DataObject with that appId exists.
   * @throws BadRequestException when {@code name} or {@code payload} are missing.
   */
  public FileReference createSingleton(String dataObjectAppId, String name, String filename, InputStream payload, long declaredSize) {
    if (name == null || name.isBlank()) {
      throw new BadRequestException("name must not be null or blank");
    }
    if (filename == null || filename.isBlank()) {
      throw new BadRequestException("filename must not be null or blank");
    }
    if (payload == null) {
      throw new BadRequestException("file payload must not be null");
    }

    DataObject parent = resolveDataObjectByAppId(dataObjectAppId);
    if (parent == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }

    ensureSharedNamespace();

    // PARSER-SPI: check cheaply (filename only) whether any file-format parser accepts
    // this file before buffering bytes. Only if a parser wants it do we read all bytes
    // into a heap buffer — for unrecognised extensions we stream directly to GridFS.
    boolean anyAccepts = parserRegistry != null && parserRegistry.anyAccepts(null, filename);
    byte[] parseBuf = null;
    ShepardFile saved;
    if (anyAccepts) {
      try {
        parseBuf = payload.readAllBytes();
      } catch (IOException e) {
        // Could not buffer — fall back to streaming (no parse annotations).
        Log.warnf(e, "FileParserRegistry: could not buffer payload for '%s'; skipping parse", filename);
        parseBuf = null;
      }
      if (parseBuf != null) {
        // MONGO-AUDIT-2026-05-24-012: enforce the upload size cap before writing to the active store.
        saved = fileStorageService.storeFile(SHARED_FILES_NAMESPACE, filename, new ByteArrayInputStream(parseBuf), declaredSize);
      } else {
        saved = fileStorageService.storeFile(SHARED_FILES_NAMESPACE, filename, payload, declaredSize);
      }
    } else {
      // MONGO-AUDIT-2026-05-24-012: enforce the upload size cap before writing to the active store.
      saved = fileStorageService.storeFile(SHARED_FILES_NAMESPACE, filename, payload, declaredSize);
    }

    User user = userService.getCurrentUser();

    FileReference singleton = new FileReference();
    singleton.setName(name);
    singleton.setDataObject(parent);
    singleton.setFile(saved);
    singleton.setFileKind(detectFileKind(filename, saved));
    singleton.setCreatedAt(dateHelper.getDate());
    singleton.setCreatedBy(user);

    FileReference created = singletonFileReferenceDAO.createOrUpdate(singleton);
    created.setShepardId(created.getId());
    created = singletonFileReferenceDAO.createOrUpdate(created);

    Log.debugf(
      "FR1b: created singleton FileReference appId=%s under DataObject appId=%s (file oid=%s)",
      created.getAppId(),
      dataObjectAppId,
      saved.getOid()
    );

    // PARSER-SPI: secondary write — fire all matching file-format parsers.
    // Fire-and-forget: exceptions are caught inside runAll and logged as WARN.
    if (parseBuf != null) {
      final byte[] buf = parseBuf;
      final String refAppId = created.getAppId();
      try {
        parserRegistry.runAll(buf, filename, refAppId, dataObjectAppId);
      } catch (Exception e) {
        Log.warnf(e, "FileParserRegistry: secondary write failed for filename='%s' appId=%s", filename, refAppId);
      }
    }

    return created;
  }

  /**
   * Create a new singleton attached to a parent DataObject.
   *
   * <p>Stores the uploaded byte stream in the shared
   * {@link #SHARED_FILES_NAMESPACE} via {@link FileService#createFile},
   * mints a fresh {@link FileReference}, attaches the resulting
   * {@link ShepardFile} via {@code HAS_PAYLOAD}, and persists the
   * Reference under {@code (DataObject)-[:has_reference]->}.
   *
   * <p>Prefer {@link #createSingleton(String, String, String, InputStream, long)} when
   * the caller knows the declared file size so the upload size cap can be enforced.
   *
   * @param dataObjectAppId parent DataObject's appId. Required.
   * @param name human-readable name for the Reference (the singleton's
   *   own name; the file's own filename is taken from the upload).
   *   Required, non-blank.
   * @param filename original filename of the upload — passed through
   *   to MongoDB / GridFS so a later download returns the same name.
   * @param payload byte stream of the file body. Required, non-null.
   * @return the persisted singleton with its attached file.
   * @throws NotFoundException when no DataObject with that appId exists.
   * @throws BadRequestException when {@code name} or {@code payload}
   *   are missing.
   */
  public FileReference createSingleton(String dataObjectAppId, String name, String filename, InputStream payload) {
    if (name == null || name.isBlank()) {
      throw new BadRequestException("name must not be null or blank");
    }
    if (filename == null || filename.isBlank()) {
      throw new BadRequestException("filename must not be null or blank");
    }
    if (payload == null) {
      throw new BadRequestException("file payload must not be null");
    }

    DataObject parent = resolveDataObjectByAppId(dataObjectAppId);
    if (parent == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }

    ensureSharedNamespace();

    // PARSER-SPI: check cheaply (filename only) whether any file-format parser accepts
    // this file before buffering bytes. Only if a parser wants it do we read all bytes
    // into a heap buffer — for unrecognised extensions we stream directly to GridFS.
    boolean anyAccepts = parserRegistry != null && parserRegistry.anyAccepts(null, filename);
    byte[] parseBuf = null;
    ShepardFile saved;
    if (anyAccepts) {
      try {
        parseBuf = payload.readAllBytes();
      } catch (IOException e) {
        Log.warnf(e, "FileParserRegistry: could not buffer payload for '%s'; skipping parse", filename);
        parseBuf = null;
      }
      if (parseBuf != null) {
        // Persist the file bytes first; the resulting ShepardFile carries
        // its own oid, md5, fileSize from the active store.
        saved = fileStorageService.storeFile(SHARED_FILES_NAMESPACE, filename, new ByteArrayInputStream(parseBuf), 0L);
      } else {
        saved = fileStorageService.storeFile(SHARED_FILES_NAMESPACE, filename, payload, 0L);
      }
    } else {
      // Persist the file bytes first; the resulting ShepardFile carries
      // its own oid, md5, fileSize from the active store.
      saved = fileStorageService.storeFile(SHARED_FILES_NAMESPACE, filename, payload, 0L);
    }

    User user = userService.getCurrentUser();

    FileReference singleton = new FileReference();
    singleton.setName(name);
    singleton.setDataObject(parent);
    singleton.setFile(saved);
    singleton.setFileKind(detectFileKind(filename, saved));
    singleton.setCreatedAt(dateHelper.getDate());
    singleton.setCreatedBy(user);

    FileReference created = singletonFileReferenceDAO.createOrUpdate(singleton);
    created.setShepardId(created.getId());
    created = singletonFileReferenceDAO.createOrUpdate(created);

    Log.debugf(
      "FR1b: created singleton FileReference appId=%s under DataObject appId=%s (file oid=%s)",
      created.getAppId(),
      dataObjectAppId,
      saved.getOid()
    );

    // PARSER-SPI: secondary write — fire all matching file-format parsers.
    // Fire-and-forget: exceptions are caught inside runAll and logged as WARN.
    if (parseBuf != null) {
      final byte[] buf = parseBuf;
      final String refAppId = created.getAppId();
      try {
        parserRegistry.runAll(buf, filename, refAppId, dataObjectAppId);
      } catch (Exception e) {
        Log.warnf(e, "FileParserRegistry: secondary write failed for filename='%s' appId=%s", filename, refAppId);
      }
    }

    return created;
  }

  /**
   * V2CONV-A2 / FILEKIND-DETECTOR-SPI — derive the {@code fileKind}
   * discriminator from the original filename extension by delegating to
   * the pluggable {@link FileKindDetectorRegistry}.
   *
   * <p>The registry iterates all {@link de.dlr.shepard.spi.payload.FileKindDetector}
   * beans (core + plugins) and returns the first match. The built-in mappings
   * are provided by {@link de.dlr.shepard.spi.payload.BuiltinFileKindDetector}:
   * {@code .krl|.src → "krl"}, {@code .svdx → "svdx"},
   * {@code .otvis → "otvis"}, {@code .urdf → "urdf"},
   * {@code .xit → "xit"}, {@code .pdf → "pdf"},
   * {@code .urscript|.script → "urscript"}. Anything else (or a
   * blank/extension-less name) yields {@code null} — the schema-additive
   * "absent means unknown" contract.
   *
   * @param filename the original upload filename (may be blank/null).
   * @param file the persisted {@link ShepardFile} (reserved for a future
   *   mime-based tie-breaker; unused today since {@link ShepardFile}
   *   carries no mime type).
   * @return the detected kind token, or {@code null} when unrecognised.
   */
  String detectFileKind(String filename, ShepardFile file) {
    String ext = extensionOf(filename);
    if (ext == null) return null;
    return detectorRegistry.resolveFileKind(ext);
  }

  /**
   * Lower-cased extension of a filename, or {@code null} when the name
   * is blank or carries no {@code .ext} suffix.
   */
  private String extensionOf(String filename) {
    if (filename == null || filename.isBlank()) return null;
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) return null;
    return filename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
  }

  /**
   * Apply an RFC 7396 merge-patch to a singleton. The only currently
   * patchable field is {@code name} — everything else (the underlying
   * file, audit-trail, the parent DataObject) is immutable for FR1b.
   *
   * @param appId the singleton's appId.
   * @param patch merge-patch body. Keys absent from the map preserve
   *   the existing value; explicit-{@code null} on {@code name} is
   *   rejected (name must remain non-blank).
   * @return the patched singleton.
   * @throws NotFoundException when no singleton with that appId exists.
   * @throws BadRequestException when the body is invalid.
   */
  public FileReference patchSingleton(String appId, Map<String, Object> patch) {
    FileReference ref = singletonFileReferenceDAO.findByAppId(appId);
    if (ref == null) {
      throw new NotFoundException("No singleton FileReference with appId " + appId);
    }
    if (patch == null) {
      throw new BadRequestException("PATCH body must be a JSON object");
    }

    if (patch.containsKey("name")) {
      Object v = patch.get("name");
      if (v == null || (v instanceof String s && s.isBlank())) {
        throw new BadRequestException("name must not be null or blank");
      }
      ref.setName(v.toString());
    }

    ref.setUpdatedAt(dateHelper.getDate());
    ref.setUpdatedBy(userService.getCurrentUser());
    return singletonFileReferenceDAO.createOrUpdate(ref);
  }

  /**
   * Hard-delete the singleton and its underlying file (Neo4j node +
   * shared-namespace Mongo doc + GridFS blob). Snapshot/version-pin
   * checks land with FR1c; FR1b ships a straight cascade.
   *
   * @param appId the singleton's appId.
   * @throws NotFoundException when no singleton with that appId exists.
   */
  public void deleteSingleton(String appId) {
    FileReference ref = singletonFileReferenceDAO.findByAppId(appId);
    if (ref == null) {
      throw new NotFoundException("No singleton FileReference with appId " + appId);
    }
    ShepardFile file = ref.getFile();
    String fileOid = file != null ? file.getOid() : null;

    // Soft-delete the Neo4j node (matches the rest of the codebase's
    // soft-delete posture for Reference primitives).
    ref.setDeleted(true);
    ref.setUpdatedAt(dateHelper.getDate());
    ref.setUpdatedBy(userService.getCurrentUser());
    singletonFileReferenceDAO.createOrUpdate(ref);

    // Hard-delete the byte payload — there's no other Reference
    // pointing at it (singletons own their file by construction).
    // Routes through the active/per-row storage adapter (idempotent).
    if (fileOid != null) {
      try {
        fileStorageService.deleteFile(SHARED_FILES_NAMESPACE, file);
      } catch (NotFoundException nfe) {
        // Already gone — log and continue. The Neo4j-side soft-delete
        // already took effect; no need to fail the whole operation
        // over an idempotent storage no-op.
        Log.warnf("FR1b: stored blob for singleton appId=%s oid=%s already missing", appId, fileOid);
      }
    }
    Log.debugf("FR1b: deleted singleton FileReference appId=%s (oid=%s)", appId, fileOid);
  }

  /**
   * Return a {@link NamedInputStream} over the singleton's bytes.
   *
   * @param appId the singleton's appId.
   * @return the byte stream; never {@code null}.
   * @throws NotFoundException when the singleton, its file, or the
   *   underlying GridFS blob is missing.
   */
  public NamedInputStream getPayload(String appId) {
    FileReference ref = singletonFileReferenceDAO.findByAppId(appId);
    if (ref == null) {
      throw new NotFoundException("No singleton FileReference with appId " + appId);
    }
    ShepardFile file = ref.getFile();
    if (file == null || file.getOid() == null) {
      throw new NotFoundException("Singleton FileReference appId=" + appId + " has no attached file");
    }
    return fileStorageService.getPayload(SHARED_FILES_NAMESPACE, file);
  }

  /**
   * Lazy initialiser for the shared {@code shepard-files} Mongo
   * collection. Calling {@code createFileContainer} (which creates a
   * fresh randomly-named collection) is the wrong shape here — we
   * want a fixed name. Mongo silently creates the collection on
   * first write, so this method is effectively a no-op today; the
   * call site is retained as a deliberate seam so a future FS1
   * (per {@code aidocs/45}) S3-backing toggle can inject creation
   * logic in one place.
   */
  private void ensureSharedNamespace() {
    // Intentionally empty — Mongo creates the collection on first
    // write. FS1 will replace this with an explicit
    // shared-storage-backend init.
  }

  /**
   * Resolve the OGM Long id of a DataObject by its appId. Returns
   * {@code null} when no DataObject with that appId exists.
   *
   * <p>Exposed to {@link
   * de.dlr.shepard.v2.file.resources.FileReferenceV2Rest} so the
   * upload endpoint can run the permission gate against the parent
   * DataObject <em>before</em> minting the singleton entity.
   *
   * @param appId the DataObject's appId.
   * @return the OGM Long id, or {@code null} when no match.
   */
  public Long getDataObjectOgmId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    try {
      return entityIdResolver.resolveLong(appId);
    } catch (NotFoundException e) {
      return null;
    }
  }

  /**
   * Resolve a DataObject by its appId via the OGM-Long bridge in
   * {@link EntityIdResolver}.
   */
  private DataObject resolveDataObjectByAppId(String appId) {
    if (appId == null) return null;
    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(appId);
    } catch (NotFoundException e) {
      return null;
    }
    DataObject dataObject = dataObjectDAO.findByNeo4jId(ogmId);
    if (dataObject == null || dataObject.isDeleted()) {
      return null;
    }
    return dataObject;
  }
}
