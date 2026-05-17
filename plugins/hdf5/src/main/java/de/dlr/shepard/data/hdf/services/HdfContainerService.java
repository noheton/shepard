package de.dlr.shepard.data.hdf.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.AbstractContainerService;
import de.dlr.shepard.data.hdf.daos.HdfContainerDAO;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.hsds.HsdsClient;
import de.dlr.shepard.data.hdf.hsds.HsdsClient.ExportResponse;
import de.dlr.shepard.data.hdf.io.HdfContainerIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;

/**
 * A5a Phase 1 — CRUD for {@link HdfContainer}, brokering the HSDS
 * sidecar provisioning calls via {@link HsdsClient}.
 *
 * <p>The HSDS round-trip happens <strong>before</strong> the Neo4j
 * commit on create (so a failed sidecar leaves no orphan shepard
 * row) and <strong>after</strong> the Neo4j soft-delete on delete
 * (so a failed sidecar leaves the shepard row tombstoned for a
 * later admin retry rather than re-exposed to readers).
 *
 * <p>The {@link HsdsClient} is injected via an {@link Instance}
 * lookup so a feature-OFF deployment never instantiates it (it's
 * registered with {@code @LookupIfProperty}). If a caller hits this
 * service while the feature is off, {@link #requireHsdsAvailable()}
 * surfaces a clean operator-readable error rather than a NPE.
 */
@RequestScoped
public class HdfContainerService extends AbstractContainerService<HdfContainer, HdfContainerIO> {

  @Inject
  HdfContainerDAO hdfContainerDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  Instance<HsdsClient> hsdsClientInstance;

  /**
   * Resolves to the prefix every HSDS domain we provision lives
   * under. Carved out here so admins can {@code grep} the prefix
   * out of HSDS-side audit logs.
   */
  static final String HSDS_DOMAIN_PREFIX = "/shepard/";

  /**
   * Create a new HdfContainer. Provisions the HSDS domain first;
   * if that fails, no Neo4j row is written (fail-fast).
   *
   * @param containerIO the wire payload — only {@code name},
   *                    {@code description}, and {@code attributes}
   *                    are consumed.
   * @return the persisted container including its server-minted
   *         {@code appId} and {@code hsdsDomain}.
   */
  @Override
  public HdfContainer createContainer(HdfContainerIO containerIO) {
    HsdsClient hsds = requireHsdsAvailable();
    User user = userService.getCurrentUser();

    HdfContainer toCreate = new HdfContainer();
    // Mint appId up-front so we can compute the HSDS domain path
    // ahead of the Neo4j commit. The DAO's createOrUpdate idiom
    // would otherwise mint a fresh one on save.
    String appId = AppIdGenerator.next();
    toCreate.setAppId(appId);
    toCreate.setName(containerIO.getName());
    toCreate.setDescription(containerIO.getDescription());
    toCreate.setAttributes(containerIO.getAttributes() == null ? new HashMap<>() : new HashMap<>(containerIO.getAttributes()));
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);

    String domain = HSDS_DOMAIN_PREFIX + appId + "/";
    toCreate.setHsdsDomain(domain);

    // Provision the HSDS side first — fail-fast leaves Neo4j untouched.
    hsds.createDomain(domain);
    Log.infof("HSDS domain provisioned for new HdfContainer appId=%s domain=%s", appId, domain);

    HdfContainer created;
    try {
      created = hdfContainerDAO.createOrUpdate(toCreate);
    } catch (RuntimeException e) {
      // Best-effort compensation: drop the freshly-provisioned HSDS domain
      // so we don't leak storage on Neo4j commit failure. Swallow any
      // compensating error and surface the original — the admin needs the
      // root cause, not a layered cleanup failure.
      Log.errorf(e, "Neo4j write failed for HdfContainer appId=%s; rolling back HSDS domain", appId);
      try {
        hsds.deleteDomain(domain);
      } catch (RuntimeException rollback) {
        Log.errorf(rollback, "HSDS rollback for domain=%s also failed — manual cleanup required", domain);
      }
      throw e;
    }

    permissionsService.createPermissions(created, user, PermissionType.Private);
    return created;
  }

  @Override
  public HdfContainer getContainer(long id) {
    HdfContainer container = hdfContainerDAO.findByNeo4jId(id);
    if (container == null || container.isDeleted()) {
      String msg = "ID ERROR - HDF Container with id %s is null or deleted".formatted(id);
      Log.error(msg);
      throw new InvalidPathException(msg);
    }
    assertIsAllowedToReadContainer(id);
    return container;
  }

  /**
   * appId-keyed lookup for the {@code /v2/} surface.
   *
   * @param appId UUID v7 identifying the container.
   * @return the container.
   * @throws InvalidPathException if no row matches or it's soft-deleted.
   */
  public HdfContainer getContainerByAppId(String appId) {
    HdfContainer container = hdfContainerDAO.findByAppId(appId);
    if (container == null || container.isDeleted()) {
      String msg = "ID ERROR - HDF Container with appId %s is null or deleted".formatted(appId);
      Log.error(msg);
      throw new InvalidPathException(msg);
    }
    assertIsAllowedToReadContainer(container.getId());
    return container;
  }

  @Override
  public List<HdfContainer> getAllContainers(QueryParamHelper params) {
    User user = userService.getCurrentUser();
    return hdfContainerDAO.findAllHdfContainers(params, user.getUsername());
  }

  @Override
  public void deleteContainer(long id) {
    HsdsClient hsds = requireHsdsAvailable();
    User user = userService.getCurrentUser();
    HdfContainer container = getContainer(id);
    assertIsAllowedToDeleteContainer(id);

    String domain = container.getHsdsDomain();
    container.setDeleted(true);
    container.setUpdatedAt(dateHelper.getDate());
    container.setUpdatedBy(user);
    hdfContainerDAO.createOrUpdate(container);
    if (domain != null && !domain.isBlank()) {
      hsds.deleteDomain(domain);
      Log.infof("HSDS domain dropped for deleted HdfContainer id=%s domain=%s", id, domain);
    }
  }

  /**
   * Delete by appId. Same semantics as {@link #deleteContainer(long)}
   * but addressable via the L2d-style identifier.
   *
   * @param appId UUID v7 identifying the container.
   */
  public void deleteContainerByAppId(String appId) {
    HdfContainer container = hdfContainerDAO.findByAppId(appId);
    if (container == null || container.isDeleted()) {
      throw new InvalidPathException("ID ERROR - HDF Container with appId %s is null or deleted".formatted(appId));
    }
    deleteContainer(container.getId());
  }

  /**
   * A5d — download the raw HDF5 bytes for the given container from HSDS.
   *
   * <p>Delegates to {@link HsdsClient#exportFile} with the optional
   * {@code Range} header passed through verbatim.  The container is
   * supplied pre-fetched (permission-checked by the caller via
   * {@link #getContainerByAppId}) to avoid a redundant lookup.
   * Returns the streaming {@link ExportResponse} — caller is
   * responsible for closing the body.
   *
   * @param container   the pre-fetched, permission-checked container.
   * @param rangeHeader Optional {@code Range} header from the client
   *                    (e.g. {@code bytes=0-1023}). May be {@code null}.
   * @return streaming HSDS export response.
   * @throws HsdsClient.HsdsException if the HSDS sidecar returns an error.
   */
  public ExportResponse downloadFile(HdfContainer container, String rangeHeader) {
    HsdsClient hsds = requireHsdsAvailable();
    String domain = container.getHsdsDomain();
    if (domain == null || domain.isBlank()) {
      throw new HsdsClient.HsdsException(
        "HdfContainer appId=" + container.getAppId() + " has no hsdsDomain — cannot export file"
      );
    }
    Log.infof("HDF exportFile requested for appId=%s domain=%s", container.getAppId(), domain);
    return hsds.exportFile(domain, rangeHeader);
  }

  /**
   * Guard for "feature toggled off" callers: the {@link HsdsClient}
   * bean is conditional on {@code shepard.hdf.enabled=true}, so when
   * the feature is off the {@link Instance} lookup is unsatisfied.
   * We surface that as an operator-readable error rather than a
   * mysterious NPE.
   */
  HsdsClient requireHsdsAvailable() {
    if (hsdsClientInstance == null || hsdsClientInstance.isUnsatisfied()) {
      throw new IllegalStateException(
        "HDF/HSDS feature is off (shepard.hdf.enabled=false). Enable the toggle and supply " +
        "shepard.hdf.hsds.{endpoint,username,password} to use HdfContainer endpoints."
      );
    }
    return hsdsClientInstance.get();
  }
}
