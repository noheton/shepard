package de.dlr.shepard.v2.shapes.repositories;

import de.dlr.shepard.v2.shapes.io.PredicateVocabularyEntryIO;
import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * JDBC read-path for the {@code predicate_vocabulary} Postgres table.
 *
 * <p>Substrate-routing table for SHACL shape writes (PR-5 enabler).
 * Populated once by {@code V1.16.0__Add_predicate_vocabulary.sql}
 * and enriched by subsequent migrations; application code only reads.
 *
 * <p>Uses {@link AgroalDataSource} + try-with-resources, same pattern
 * as {@link de.dlr.shepard.v2.admin.services.PermissionAuditLogQueryService}.
 * {@code @ApplicationScoped} because the vocabulary is effectively static
 * for the lifetime of a deployment.
 *
 * @see <a href="../../../../../../aidocs/semantics/98-shapes-views-and-process-model.md">aidocs/98 §1.3</a>
 */
@ApplicationScoped
public class PredicateVocabularyRepository {

  private static final String SELECT_ALL =
    "SELECT predicate_uri, substrate, cardinality, writable, description, shape_file, added_at " +
    "FROM predicate_vocabulary ORDER BY predicate_uri";

  private static final String SELECT_ALL_PAGED =
    "SELECT predicate_uri, substrate, cardinality, writable, description, shape_file, added_at " +
    "FROM predicate_vocabulary ORDER BY predicate_uri LIMIT ? OFFSET ?";

  private static final String SELECT_BY_URI =
    "SELECT predicate_uri, substrate, cardinality, writable, description, shape_file, added_at " +
    "FROM predicate_vocabulary WHERE predicate_uri = ?";

  private static final String SELECT_BY_SUBSTRATE =
    "SELECT predicate_uri, substrate, cardinality, writable, description, shape_file, added_at " +
    "FROM predicate_vocabulary WHERE substrate = ? ORDER BY predicate_uri";

  private static final String SELECT_BY_SUBSTRATE_PAGED =
    "SELECT predicate_uri, substrate, cardinality, writable, description, shape_file, added_at " +
    "FROM predicate_vocabulary WHERE substrate = ? ORDER BY predicate_uri LIMIT ? OFFSET ?";

  private static final String COUNT_ALL =
    "SELECT count(*) FROM predicate_vocabulary";

  private static final String COUNT_BY_SUBSTRATE =
    "SELECT count(*) FROM predicate_vocabulary WHERE substrate = ?";

  @Inject
  AgroalDataSource defaultDataSource;

  /**
   * Returns all vocabulary entries, ordered by {@code predicate_uri}.
   *
   * @return all rows; empty list on error (never null)
   */
  public List<PredicateVocabularyEntryIO> findAll() {
    try (Connection conn = defaultDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
         ResultSet rs = ps.executeQuery()) {
      return mapRows(rs);
    } catch (Exception e) {
      Log.warnf(e, "predicate_vocabulary: findAll failed; returning empty list");
      return Collections.emptyList();
    }
  }

  /**
   * Looks up a single predicate by its full IRI.
   *
   * @param predicateUri absolute IRI, e.g.
   *   {@code http://semantics.dlr.de/shepard-upper#status}
   * @return the matching entry, or {@link Optional#empty()} if not found
   */
  public Optional<PredicateVocabularyEntryIO> findByPredicate(String predicateUri) {
    if (predicateUri == null || predicateUri.isBlank()) {
      return Optional.empty();
    }
    try (Connection conn = defaultDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(SELECT_BY_URI)) {
      ps.setString(1, predicateUri.trim());
      try (ResultSet rs = ps.executeQuery()) {
        List<PredicateVocabularyEntryIO> rows = mapRows(rs);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
      }
    } catch (Exception e) {
      Log.warnf(e, "predicate_vocabulary: findByPredicate(%s) failed", predicateUri);
      return Optional.empty();
    }
  }

  /**
   * Returns all predicates routed to a given substrate.
   *
   * @param substrate one of {@code neo4j}, {@code timescaledb},
   *   {@code postgres}, {@code garage}
   * @return matching rows; empty list on error or unknown substrate
   */
  public List<PredicateVocabularyEntryIO> findBySubstrate(String substrate) {
    if (substrate == null || substrate.isBlank()) {
      return Collections.emptyList();
    }
    try (Connection conn = defaultDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(SELECT_BY_SUBSTRATE)) {
      ps.setString(1, substrate.trim());
      try (ResultSet rs = ps.executeQuery()) {
        return mapRows(rs);
      }
    } catch (Exception e) {
      Log.warnf(e, "predicate_vocabulary: findBySubstrate(%s) failed", substrate);
      return Collections.emptyList();
    }
  }

  /**
   * Returns a page of vocabulary entries ordered by {@code predicate_uri}.
   *
   * @param skip number of rows to skip (OFFSET)
   * @param limit max rows to return (LIMIT)
   * @return matching rows; empty list on error
   */
  public List<PredicateVocabularyEntryIO> findAll(int skip, int limit) {
    try (Connection conn = defaultDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(SELECT_ALL_PAGED)) {
      ps.setInt(1, limit);
      ps.setInt(2, skip);
      try (ResultSet rs = ps.executeQuery()) {
        return mapRows(rs);
      }
    } catch (Exception e) {
      Log.warnf(e, "predicate_vocabulary: findAll(skip=%d, limit=%d) failed; returning empty list", skip, limit);
      return Collections.emptyList();
    }
  }

  /**
   * Returns a page of predicates routed to a given substrate.
   *
   * @param substrate one of {@code neo4j}, {@code timescaledb},
   *   {@code postgres}, {@code garage}
   * @param skip number of rows to skip (OFFSET)
   * @param limit max rows to return (LIMIT)
   * @return matching rows; empty list on error or unknown substrate
   */
  public List<PredicateVocabularyEntryIO> findBySubstrate(String substrate, int skip, int limit) {
    if (substrate == null || substrate.isBlank()) {
      return Collections.emptyList();
    }
    try (Connection conn = defaultDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(SELECT_BY_SUBSTRATE_PAGED)) {
      ps.setString(1, substrate.trim());
      ps.setInt(2, limit);
      ps.setInt(3, skip);
      try (ResultSet rs = ps.executeQuery()) {
        return mapRows(rs);
      }
    } catch (Exception e) {
      Log.warnf(e, "predicate_vocabulary: findBySubstrate(%s, skip=%d, limit=%d) failed", substrate, skip, limit);
      return Collections.emptyList();
    }
  }

  /**
   * Returns the total number of vocabulary entries.
   *
   * @return row count; 0 on error
   */
  public long count() {
    try (Connection conn = defaultDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(COUNT_ALL);
         ResultSet rs = ps.executeQuery()) {
      return rs.next() ? rs.getLong(1) : 0L;
    } catch (Exception e) {
      Log.warnf(e, "predicate_vocabulary: count() failed; returning 0");
      return 0L;
    }
  }

  /**
   * Returns the number of vocabulary entries for a given substrate.
   *
   * @param substrate substrate name
   * @return row count; 0 on error or blank input
   */
  public long countBySubstrate(String substrate) {
    if (substrate == null || substrate.isBlank()) {
      return 0L;
    }
    try (Connection conn = defaultDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(COUNT_BY_SUBSTRATE)) {
      ps.setString(1, substrate.trim());
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getLong(1) : 0L;
      }
    } catch (Exception e) {
      Log.warnf(e, "predicate_vocabulary: countBySubstrate(%s) failed; returning 0", substrate);
      return 0L;
    }
  }

  // ─── private helpers ───────────────────────────────────────────────────────

  private List<PredicateVocabularyEntryIO> mapRows(ResultSet rs) throws Exception {
    List<PredicateVocabularyEntryIO> out = new ArrayList<>();
    while (rs.next()) {
      Timestamp ts = rs.getTimestamp("added_at");
      out.add(new PredicateVocabularyEntryIO(
        rs.getString("predicate_uri"),
        rs.getString("substrate"),
        rs.getString("cardinality"),
        rs.getBoolean("writable"),
        rs.getString("description"),
        rs.getString("shape_file"),
        ts != null ? ts.toInstant().toString() : null
      ));
    }
    return out;
  }
}
