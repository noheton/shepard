package de.dlr.shepard.v2.admin.services;

import de.dlr.shepard.v2.admin.io.PermissionAuditLogEntryIO;
import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * F3 — read side for {@code GET /v2/admin/permission-audit/log}.
 *
 * <p>Supports filtering by {@code entityAppId}, {@code actor}, and a half-open
 * time window [{@code from}, {@code to}). Results are sorted {@code occurred_at DESC}
 * and paged via {@code LIMIT}/{@code OFFSET}.
 */
@ApplicationScoped
public class PermissionAuditLogQueryService {

  @Inject
  AgroalDataSource defaultDataSource;

  /**
   * Query the permission audit log with optional filters.
   *
   * @param entityAppId filter by entity appId (null = no filter)
   * @param actor       filter by actor_username (null = no filter)
   * @param from        filter to rows with occurred_at &gt;= from (null = no lower bound)
   * @param to          filter to rows with occurred_at &lt; to (null = no upper bound)
   * @param page        zero-based page index
   * @param size        page size (capped at 500 internally)
   * @return matching rows, sorted occurred_at DESC
   */
  public List<PermissionAuditLogEntryIO> query(
    String entityAppId,
    String actor,
    Instant from,
    Instant to,
    int page,
    int size
  ) {
    int effectiveSize = Math.min(Math.max(size, 1), 500);
    int effectivePage = Math.max(page, 0);

    StringBuilder sql = new StringBuilder(
      "SELECT id, occurred_at, entity_app_id, entity_kind, actor_username, action, detail_json " +
      "FROM permission_audit_log WHERE 1=1 "
    );
    List<Object> params = new ArrayList<>();

    if (entityAppId != null && !entityAppId.isBlank()) {
      sql.append("AND entity_app_id = ? ");
      params.add(entityAppId.trim());
    }
    if (actor != null && !actor.isBlank()) {
      sql.append("AND actor_username = ? ");
      params.add(actor.trim());
    }
    if (from != null) {
      sql.append("AND occurred_at >= ? ");
      params.add(Timestamp.from(from));
    }
    if (to != null) {
      sql.append("AND occurred_at < ? ");
      params.add(Timestamp.from(to));
    }

    sql.append("ORDER BY occurred_at DESC LIMIT ? OFFSET ?");
    params.add(effectiveSize);
    params.add((long) effectivePage * effectiveSize);

    try (Connection conn = defaultDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql.toString())) {
      for (int i = 0; i < params.size(); i++) {
        Object p = params.get(i);
        if (p instanceof String s) ps.setString(i + 1, s);
        else if (p instanceof Timestamp ts) ps.setTimestamp(i + 1, ts);
        else if (p instanceof Integer iv) ps.setInt(i + 1, iv);
        else if (p instanceof Long lv) ps.setLong(i + 1, lv);
        else ps.setObject(i + 1, p);
      }
      try (ResultSet rs = ps.executeQuery()) {
        List<PermissionAuditLogEntryIO> out = new ArrayList<>();
        while (rs.next()) {
          Timestamp ts = rs.getTimestamp("occurred_at");
          out.add(new PermissionAuditLogEntryIO(
            rs.getLong("id"),
            ts != null ? ts.toInstant().toString() : null,
            rs.getString("entity_app_id"),
            rs.getString("entity_kind"),
            rs.getString("actor_username"),
            rs.getString("action"),
            rs.getString("detail_json")
          ));
        }
        return out;
      }
    } catch (Exception e) {
      Log.warnf(e, "F3: permission audit log query failed; returning empty list");
      return Collections.emptyList();
    }
  }
}
