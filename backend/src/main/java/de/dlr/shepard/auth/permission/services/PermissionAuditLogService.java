package de.dlr.shepard.auth.permission.services;

import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * F3 — Fire-and-forget write path for the {@code permission_audit_log} Postgres table.
 *
 * <p>Every grant / revoke / update of entity permissions records one row.
 * The service is {@code @ApplicationScoped} (no per-request state) and uses
 * the same {@link AgroalDataSource} pool used by the timeseries layer
 * ({@code SqlQueryExecutor}, {@code TimeseriesDataPointRepository}).
 *
 * <p>Contract: {@link #log} never throws. Any SQL or connection failure is
 * caught and emitted as a {@code WARN} log line so that the permissions write
 * path is never blocked by an audit outage.
 */
@ApplicationScoped
public class PermissionAuditLogService {

  private static final String INSERT_SQL =
    "INSERT INTO permission_audit_log " +
    "(occurred_at, entity_app_id, entity_kind, actor_username, action, detail_json) " +
    "VALUES (?, ?, ?, ?, ?, ?)";

  @Inject
  AgroalDataSource defaultDataSource;

  /**
   * Records a permission change event. Never throws.
   *
   * @param entityAppId   the {@code appId} of the entity whose permissions changed (required)
   * @param entityKind    human-readable label, e.g. {@code "Collection"} (nullable, best-effort)
   * @param actorUsername the caller who triggered the change, from JWT sub / API-key (nullable)
   * @param action        {@code "GRANT"}, {@code "REVOKE"}, or {@code "UPDATE"}
   * @param detailJson    JSON string describing the before/after state (nullable)
   */
  public void log(
    String entityAppId,
    String entityKind,
    String actorUsername,
    String action,
    String detailJson
  ) {
    if (entityAppId == null || action == null) {
      Log.warnf(
        "PermissionAuditLogService.log called with null entityAppId or action; skipping (actor=%s)",
        actorUsername
      );
      return;
    }
    try (Connection conn = defaultDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
      ps.setTimestamp(1, Timestamp.from(Instant.now()));
      ps.setString(2, entityAppId);
      ps.setString(3, entityKind);
      ps.setString(4, actorUsername);
      ps.setString(5, action);
      ps.setString(6, detailJson);
      ps.executeUpdate();
    } catch (Exception e) {
      Log.warnf(
        e,
        "F3: permission audit log write failed (entityAppId=%s, action=%s, actor=%s); ignoring",
        entityAppId,
        action,
        actorUsername
      );
    }
  }
}
