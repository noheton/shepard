package de.dlr.shepard.v2.admin.resources;

import com.mongodb.client.MongoDatabase;
import de.dlr.shepard.v2.admin.io.StorageOverviewIO;
import de.dlr.shepard.v2.admin.io.StorageOverviewIO.MongoStats;
import de.dlr.shepard.v2.admin.io.StorageOverviewIO.TimescaleStats;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.bson.Document;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * AD_STORE1 — aggregated storage overview for instance admins.
 *
 * <p>Gives a cross-database picture of disk usage so an admin can tell at a glance
 * whether TimescaleDB, MongoDB, or Neo4j is growing and needs attention.
 *
 * <p>Route: {@code GET /v2/admin/storage-overview}
 * Gate: {@code instance-admin} role.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/admin/storage-overview")
@RequestScoped
@RolesAllowed("instance-admin")
@Tag(name = "Admin storage overview (AD_STORE1)")
public class AdminStorageOverviewRest {

  @PersistenceContext
  EntityManager entityManager;

  @Inject
  MongoDatabase mongoDatabase;

  @GET
  @Operation(
    summary = "Aggregated storage overview across all database backends.",
    description = "Returns disk usage per backend (TimescaleDB, MongoDB) so admins can " +
      "identify which database is growing and whether capacity action is needed. " +
      "Requires instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Storage overview.",
    content = @Content(schema = @Schema(implementation = StorageOverviewIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Instance-admin role required.")
  public Response getStorageOverview() {
    return Response.ok(new StorageOverviewIO(
      buildTimescaleStats(),
      buildMongoStats()
    )).build();
  }

  private TimescaleStats buildTimescaleStats() {
    // Hypertable on-disk size (compressed + uncompressed chunks combined).
    Number hypertableSize;
    try {
      hypertableSize = (Number) entityManager
        .createNativeQuery("SELECT hypertable_size('timeseries_data_points')")
        .getSingleResult();
    } catch (Exception e) {
      Log.warnf("AD_STORE1: hypertable_size query failed — %s", e.getMessage());
      hypertableSize = 0L;
    }

    // Channel count (one row per channel in the timeseries metadata table).
    Object[] channelRow = (Object[]) entityManager
      .createNativeQuery(
        "SELECT COUNT(*) AS channel_count, COUNT(DISTINCT container_id) AS container_count " +
        "FROM timeseries"
      ).getSingleResult();
    long channelCount = ((Number) channelRow[0]).longValue();
    long containerCount = ((Number) channelRow[1]).longValue();

    // Sum of before_compression_total_bytes for chunks that have been compressed.
    Number beforeCompression;
    try {
      beforeCompression = (Number) entityManager
        .createNativeQuery(
          "SELECT COALESCE(SUM(before_compression_total_bytes), 0) " +
          "FROM timescaledb_information.chunks " +
          "WHERE hypertable_name = 'timeseries_data_points' " +
          "  AND is_compressed = true"
        ).getSingleResult();
    } catch (Exception e) {
      Log.warnf("AD_STORE1: before_compression query failed — %s", e.getMessage());
      beforeCompression = 0L;
    }

    long hypertableSizeBytes = toLong(hypertableSize);
    long uncompressedChunkBytes = toLong(beforeCompression);
    Double ratio = (hypertableSizeBytes > 0 && uncompressedChunkBytes > 0)
      ? (double) uncompressedChunkBytes / hypertableSizeBytes
      : null;

    return new TimescaleStats(hypertableSizeBytes, channelCount, containerCount, uncompressedChunkBytes, ratio);
  }

  private MongoStats buildMongoStats() {
    try {
      Document stats = mongoDatabase.runCommand(new Document("dbStats", 1));
      long storageSize = toLong(stats.get("storageSize"));
      long dataSize = toLong(stats.get("dataSize"));
      long indexSize = toLong(stats.get("indexSize"));
      int collections = stats.getInteger("collections", 0);
      return new MongoStats(storageSize, dataSize, indexSize, collections);
    } catch (Exception e) {
      Log.warnf("AD_STORE1: MongoDB dbStats failed — %s", e.getMessage());
      return new MongoStats(0, 0, 0, 0);
    }
  }

  private static long toLong(Object value) {
    if (value == null) return 0L;
    if (value instanceof Long l) return l;
    if (value instanceof Integer i) return i.longValue();
    if (value instanceof BigDecimal bd) return bd.longValue();
    if (value instanceof BigInteger bi) return bi.longValue();
    if (value instanceof Number n) return n.longValue();
    if (value instanceof Double d) return d.longValue();
    return 0L;
  }
}
