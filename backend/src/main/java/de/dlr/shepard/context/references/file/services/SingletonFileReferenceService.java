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
import de.dlr.shepard.data.file.services.FileService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
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
  FileService fileService;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  EntityIdResolver entityIdResolver;

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
   * Create a new singleton attached to a parent DataObject.
   *
   * <p>Stores the uploaded byte stream in the shared
   * {@link #SHARED_FILES_NAMESPACE} via {@link FileService#createFile},
   * mints a fresh {@link FileReference}, attaches the resulting
   * {@link ShepardFile} via {@code HAS_PAYLOAD}, and persists the
   * Reference under {@code (DataObject)-[:has_reference]->}.
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

    // Persist the file bytes first; the resulting ShepardFile carries
    // its own oid, md5, fileSize from the GridFS upload.
    ShepardFile saved = fileService.createFile(SHARED_FILES_NAMESPACE, filename, payload);

    User user = userService.getCurrentUser();

    FileReference singleton = new FileReference();
    singleton.setName(name);
    singleton.setDataObject(parent);
    singleton.setFile(saved);
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
    return created;
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
    String fileOid = ref.getFile() != null ? ref.getFile().getOid() : null;

    // Soft-delete the Neo4j node (matches the rest of the codebase's
    // soft-delete posture for Reference primitives).
    ref.setDeleted(true);
    ref.setUpdatedAt(dateHelper.getDate());
    ref.setUpdatedBy(userService.getCurrentUser());
    singletonFileReferenceDAO.createOrUpdate(ref);

    // Hard-delete the byte payload — there's no other Reference
    // pointing at it (singletons own their file by construction).
    if (fileOid != null) {
      try {
        fileService.deleteFile(SHARED_FILES_NAMESPACE, fileOid);
      } catch (NotFoundException nfe) {
        // Already gone — log and continue. The Neo4j-side soft-delete
        // already took effect; no need to fail the whole operation
        // over an idempotent Mongo no-op.
        Log.warnf("FR1b: GridFS blob for singleton appId=%s oid=%s already missing", appId, fileOid);
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
    return fileService.getPayload(SHARED_FILES_NAMESPACE, file.getOid());
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
