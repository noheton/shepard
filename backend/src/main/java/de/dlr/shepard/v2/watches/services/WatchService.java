package de.dlr.shepard.v2.watches.services;

import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.watches.daos.WatchDAO;
import de.dlr.shepard.v2.watches.entities.Watch;
import de.dlr.shepard.v2.watches.io.WatchIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * WATCH1 — service layer for Collection {@code :watches} Container links.
 *
 * <p>Owns the resolution of {@link WatchIO} responses — including the
 * container name + availability token — so the REST layer is a thin
 * dispatch.
 */
@ApplicationScoped
public class WatchService {

  @Inject
  WatchDAO watchDAO;

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  CollectionService collectionService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  FileContainerService fileContainerService;

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @Inject
  UserService userService;

  /** Resolve a Collection by appId, throwing 404-mappable if missing. */
  public Collection getCollection(String collectionAppId) {
    String username = userService.getCurrentUser().getUsername();
    Collection coll = collectionDAO.findByAppId(collectionAppId, username);
    if (coll == null) {
      throw new jakarta.ws.rs.NotFoundException(
        "No Collection with appId " + collectionAppId
      );
    }
    return coll;
  }

  /** List all watches on a collection, with container details inlined. */
  public List<WatchIO> list(String collectionAppId) {
    // Read auth: getCollection enforces Read perm via the DAO.
    getCollection(collectionAppId);
    List<Watch> watches = watchDAO.findByCollectionAppId(collectionAppId);
    List<WatchIO> result = new ArrayList<>(watches.size());
    for (Watch w : watches) {
      result.add(resolveContainer(WatchIO.from(w)));
    }
    return result;
  }

  /** Create a watch link, idempotent on (collection, container) pair. */
  public WatchIO create(String collectionAppId, Watch.Kind kind, String containerAppId) {
    Collection coll = getCollection(collectionAppId);
    collectionService.assertIsAllowedToEditCollection(coll.getId());

    // Verify container exists and the caller has Read on it.
    String containerName = resolveContainerName(kind, containerAppId);
    if (containerName == null) {
      throw new jakarta.ws.rs.ForbiddenException(
        "Container " + containerAppId + " (" + kind + ") not readable or doesn't exist"
      );
    }

    // Idempotent: if a watch already exists, return it.
    Watch existing = watchDAO.findByCollectionAndContainer(collectionAppId, containerAppId);
    if (existing != null) {
      return resolveContainer(WatchIO.from(existing));
    }

    Watch w = new Watch();
    w.setCollectionAppId(collectionAppId);
    w.setContainerAppId(containerAppId);
    w.setContainerKind(kind);
    w.setSince(System.currentTimeMillis());
    w.setAddedBy(userService.getCurrentUser().getUsername());
    Watch saved = watchDAO.createOrUpdate(w);
    Log.infof("WATCH1: %s -> %s (%s) by %s",
      collectionAppId, containerAppId, kind, w.getAddedBy());
    return resolveContainer(WatchIO.from(saved));
  }

  /** Remove a watch by its own appId. Idempotent. */
  public boolean delete(String collectionAppId, String watchAppId) {
    Collection coll = getCollection(collectionAppId);
    collectionService.assertIsAllowedToEditCollection(coll.getId());
    Watch w = watchDAO.findByAppId(watchAppId);
    if (w == null) return false;
    if (!collectionAppId.equals(w.getCollectionAppId())) {
      throw new jakarta.ws.rs.NotFoundException(
        "Watch " + watchAppId + " does not belong to collection " + collectionAppId
      );
    }
    watchDAO.deleteByNeo4jId(w.getId());
    Log.infof("WATCH1: removed %s from %s", watchAppId, collectionAppId);
    return true;
  }

  // ─── Container resolution helpers ──────────────────────────────

  private WatchIO resolveContainer(WatchIO io) {
    if (io.containerKind() == null || io.containerAppId() == null) {
      return io.withResolution(null, null, "error");
    }
    try {
      switch (io.containerKind()) {
        case TIMESERIES -> {
          TimeseriesContainer c = timeseriesContainerService.getContainerByAppId(io.containerAppId());
          if (c == null) return io.withResolution(null, null, "deleted");
          return io.withResolution(c.getId(), c.getName(), "available");
        }
        case FILE -> {
          FileContainer c = fileContainerService.getContainerByAppId(io.containerAppId());
          if (c == null) return io.withResolution(null, null, "deleted");
          return io.withResolution(c.getId(), c.getName(), "available");
        }
        case STRUCTURED_DATA -> {
          StructuredDataContainer c = structuredDataContainerService.getContainerByAppId(io.containerAppId());
          if (c == null) return io.withResolution(null, null, "deleted");
          return io.withResolution(c.getId(), c.getName(), "available");
        }
      }
    } catch (de.dlr.shepard.common.exceptions.InvalidAuthException e) {
      return io.withResolution(null, null, "forbidden");
    } catch (de.dlr.shepard.common.exceptions.InvalidPathException e) {
      return io.withResolution(null, null, "deleted");
    } catch (Exception e) {
      return io.withResolution(null, null, "error");
    }
    return io.withResolution(null, null, "error");
  }

  /** Returns the container's name when the caller has Read on it. */
  private String resolveContainerName(Watch.Kind kind, String containerAppId) {
    WatchIO io = new WatchIO(null, kind, containerAppId, null, null, null, null, null);
    WatchIO resolved = resolveContainer(io);
    return "available".equals(resolved.containerAvailability()) ? resolved.containerName() : null;
  }
}
