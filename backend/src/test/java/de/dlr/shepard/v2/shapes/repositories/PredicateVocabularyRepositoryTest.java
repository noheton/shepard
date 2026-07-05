package de.dlr.shepard.v2.shapes.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.shapes.io.PredicateVocabularyEntryIO;
import io.agroal.api.AgroalDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link PredicateVocabularyRepository}.
 *
 * <p>Contract verified:
 * <ol>
 *   <li>findAll() returns all rows mapped to {@link PredicateVocabularyEntryIO}</li>
 *   <li>findByPredicate() returns the matching entry or empty</li>
 *   <li>findBySubstrate("neo4j") / "garage" return filtered rows</li>
 *   <li>JDBC failures are caught — never propagate to callers</li>
 *   <li>Null / blank arguments are handled gracefully (no DB hit, empty return)</li>
 * </ol>
 */
class PredicateVocabularyRepositoryTest {

  @Mock
  AgroalDataSource defaultDataSource;

  @InjectMocks
  PredicateVocabularyRepository repository;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  // ─── findAll ───────────────────────────────────────────────────────────────

  @Test
  void findAll_returnsAllRows() throws Exception {
    ResultSet rs = twoRowResultSet();
    Connection conn = connectionWith(rs);
    when(defaultDataSource.getConnection()).thenReturn(conn);

    List<PredicateVocabularyEntryIO> rows = repository.findAll();

    assertEquals(2, rows.size());
    assertEquals("http://semantics.dlr.de/shepard-upper#status", rows.get(0).predicateUri());
    assertEquals("neo4j", rows.get(0).substrate());
    assertTrue(rows.get(0).writable());
    assertEquals("http://semantics.dlr.de/shepard-upper#hashSha256", rows.get(1).predicateUri());
    assertEquals("postgres", rows.get(1).substrate());
    assertFalse(rows.get(1).writable());
  }

  @Test
  void findAll_jdbcThrows_returnsEmptyList() throws Exception {
    when(defaultDataSource.getConnection()).thenThrow(new java.sql.SQLException("pool exhausted"));

    List<PredicateVocabularyEntryIO> rows = repository.findAll();

    assertNotNull(rows);
    assertTrue(rows.isEmpty());
  }

  // ─── findByPredicate ───────────────────────────────────────────────────────

  @Test
  void findByPredicate_found_returnsEntry() throws Exception {
    ResultSet rs = singleRowResultSet(
      "http://semantics.dlr.de/shepard-upper#status",
      "neo4j", "one", true,
      "Lifecycle status", "shepard-core-shapes.ttl"
    );
    Connection conn = connectionWith(rs);
    when(defaultDataSource.getConnection()).thenReturn(conn);

    Optional<PredicateVocabularyEntryIO> result =
      repository.findByPredicate("http://semantics.dlr.de/shepard-upper#status");

    assertTrue(result.isPresent());
    assertEquals("neo4j", result.get().substrate());
    assertEquals("one", result.get().cardinality());
    assertTrue(result.get().writable());
  }

  @Test
  void findByPredicate_notFound_returnsEmpty() throws Exception {
    ResultSet rs = emptyResultSet();
    Connection conn = connectionWith(rs);
    when(defaultDataSource.getConnection()).thenReturn(conn);

    Optional<PredicateVocabularyEntryIO> result =
      repository.findByPredicate("http://semantics.dlr.de/shepard-upper#unknownPredicate");

    assertFalse(result.isPresent());
  }

  @Test
  void findByPredicate_nullInput_returnsEmpty() {
    // Must not touch the DB
    Optional<PredicateVocabularyEntryIO> result = repository.findByPredicate(null);
    assertFalse(result.isPresent());
  }

  @Test
  void findByPredicate_blankInput_returnsEmpty() {
    Optional<PredicateVocabularyEntryIO> result = repository.findByPredicate("   ");
    assertFalse(result.isPresent());
  }

  @Test
  void findByPredicate_jdbcThrows_returnsEmpty() throws Exception {
    when(defaultDataSource.getConnection()).thenThrow(new java.sql.SQLException("timeout"));

    Optional<PredicateVocabularyEntryIO> result =
      repository.findByPredicate("http://semantics.dlr.de/shepard-upper#status");

    assertFalse(result.isPresent());
  }

  // ─── findBySubstrate ───────────────────────────────────────────────────────

  @Test
  void findBySubstrate_neo4j_returnsMatchingRows() throws Exception {
    ResultSet rs = singleRowResultSet(
      "http://semantics.dlr.de/shepard-upper#status",
      "neo4j", "one", true,
      "Lifecycle status", "shepard-core-shapes.ttl"
    );
    Connection conn = connectionWith(rs);
    when(defaultDataSource.getConnection()).thenReturn(conn);

    List<PredicateVocabularyEntryIO> rows = repository.findBySubstrate("neo4j");

    assertFalse(rows.isEmpty());
    assertEquals("neo4j", rows.get(0).substrate());
  }

  @Test
  void findBySubstrate_garage_returnsMatchingRows() throws Exception {
    ResultSet rs = singleRowResultSet(
      "http://semantics.dlr.de/shepard-upper#approvalDocument",
      "garage", "one", true,
      "IRI of approval document payload", "mini-shapes.ttl"
    );
    Connection conn = connectionWith(rs);
    when(defaultDataSource.getConnection()).thenReturn(conn);

    List<PredicateVocabularyEntryIO> rows = repository.findBySubstrate("garage");

    assertEquals(1, rows.size());
    assertEquals("garage", rows.get(0).substrate());
    assertEquals("http://semantics.dlr.de/shepard-upper#approvalDocument",
      rows.get(0).predicateUri());
  }

  @Test
  void findBySubstrate_nullInput_returnsEmpty() {
    List<PredicateVocabularyEntryIO> rows = repository.findBySubstrate(null);
    assertNotNull(rows);
    assertTrue(rows.isEmpty());
  }

  @Test
  void findBySubstrate_jdbcThrows_returnsEmptyList() throws Exception {
    when(defaultDataSource.getConnection()).thenThrow(new java.sql.SQLException("connection refused"));

    List<PredicateVocabularyEntryIO> rows = repository.findBySubstrate("neo4j");

    assertNotNull(rows);
    assertTrue(rows.isEmpty());
  }

  // ─── findAll(skip, limit) ─────────────────────────────────────────────────

  @Test
  void findAllPaged_returnsPagedRows() throws Exception {
    ResultSet rs = twoRowResultSet();
    Connection conn = connectionWith(rs);
    when(defaultDataSource.getConnection()).thenReturn(conn);

    List<PredicateVocabularyEntryIO> rows = repository.findAll(0, 10);

    assertEquals(2, rows.size());
    assertEquals("http://semantics.dlr.de/shepard-upper#status", rows.get(0).predicateUri());
  }

  @Test
  void findAllPaged_jdbcThrows_returnsEmpty() throws Exception {
    when(defaultDataSource.getConnection()).thenThrow(new java.sql.SQLException("timeout"));

    List<PredicateVocabularyEntryIO> rows = repository.findAll(0, 10);

    assertNotNull(rows);
    assertTrue(rows.isEmpty());
  }

  // ─── findBySubstrate(substrate, skip, limit) ─────────────────────────────

  @Test
  void findBySubstratePaged_returnsRows() throws Exception {
    ResultSet rs = singleRowResultSet(
      "http://semantics.dlr.de/shepard-upper#status",
      "neo4j", "one", true,
      "Lifecycle status", "shepard-core-shapes.ttl"
    );
    Connection conn = connectionWith(rs);
    when(defaultDataSource.getConnection()).thenReturn(conn);

    List<PredicateVocabularyEntryIO> rows = repository.findBySubstrate("neo4j", 0, 5);

    assertFalse(rows.isEmpty());
    assertEquals("neo4j", rows.get(0).substrate());
  }

  @Test
  void findBySubstratePaged_nullInput_returnsEmpty() {
    List<PredicateVocabularyEntryIO> rows = repository.findBySubstrate(null, 0, 10);
    assertNotNull(rows);
    assertTrue(rows.isEmpty());
  }

  @Test
  void findBySubstratePaged_jdbcThrows_returnsEmpty() throws Exception {
    when(defaultDataSource.getConnection()).thenThrow(new java.sql.SQLException("refused"));

    List<PredicateVocabularyEntryIO> rows = repository.findBySubstrate("postgres", 0, 10);

    assertNotNull(rows);
    assertTrue(rows.isEmpty());
  }

  // ─── count / countBySubstrate ─────────────────────────────────────────────

  @Test
  void count_returnsRowCount() throws Exception {
    ResultSet rs = countResultSet(7L);
    Connection conn = connectionWithCountRs(rs);
    when(defaultDataSource.getConnection()).thenReturn(conn);

    assertEquals(7L, repository.count());
  }

  @Test
  void count_jdbcThrows_returnsZero() throws Exception {
    when(defaultDataSource.getConnection()).thenThrow(new java.sql.SQLException("timeout"));

    assertEquals(0L, repository.count());
  }

  @Test
  void countBySubstrate_returnsFilteredCount() throws Exception {
    ResultSet rs = countResultSet(3L);
    Connection conn = connectionWith(rs);
    when(defaultDataSource.getConnection()).thenReturn(conn);

    assertEquals(3L, repository.countBySubstrate("neo4j"));
  }

  @Test
  void countBySubstrate_nullInput_returnsZero() {
    assertEquals(0L, repository.countBySubstrate(null));
  }

  @Test
  void countBySubstrate_jdbcThrows_returnsZero() throws Exception {
    when(defaultDataSource.getConnection()).thenThrow(new java.sql.SQLException("timeout"));

    assertEquals(0L, repository.countBySubstrate("garage"));
  }

  // ─── result-set factories ──────────────────────────────────────────────────

  /** Two-row mock RS: one neo4j predicate, one postgres predicate. */
  private ResultSet twoRowResultSet() throws Exception {
    ResultSet rs = mock(ResultSet.class);
    Timestamp ts = Timestamp.from(Instant.parse("2026-05-26T00:00:00Z"));

    when(rs.next()).thenReturn(true, true, false);
    // Row 1
    when(rs.getString("predicate_uri"))
      .thenReturn("http://semantics.dlr.de/shepard-upper#status",
                  "http://semantics.dlr.de/shepard-upper#hashSha256");
    when(rs.getString("substrate"))
      .thenReturn("neo4j", "postgres");
    when(rs.getString("cardinality"))
      .thenReturn("one", "one");
    when(rs.getBoolean("writable"))
      .thenReturn(true, false);
    when(rs.getString("description"))
      .thenReturn("Lifecycle status", "SHA-256 digest");
    when(rs.getString("shape_file"))
      .thenReturn("shepard-core-shapes.ttl", "ledger-anchor-shapes.ttl");
    when(rs.getTimestamp("added_at"))
      .thenReturn(ts, ts);
    return rs;
  }

  private ResultSet singleRowResultSet(
    String uri, String substrate, String cardinality,
    boolean writable, String description, String shapeFile
  ) throws Exception {
    ResultSet rs = mock(ResultSet.class);
    Timestamp ts = Timestamp.from(Instant.parse("2026-05-26T00:00:00Z"));

    when(rs.next()).thenReturn(true, false);
    when(rs.getString("predicate_uri")).thenReturn(uri);
    when(rs.getString("substrate")).thenReturn(substrate);
    when(rs.getString("cardinality")).thenReturn(cardinality);
    when(rs.getBoolean("writable")).thenReturn(writable);
    when(rs.getString("description")).thenReturn(description);
    when(rs.getString("shape_file")).thenReturn(shapeFile);
    when(rs.getTimestamp("added_at")).thenReturn(ts);
    return rs;
  }

  private ResultSet emptyResultSet() throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.next()).thenReturn(false);
    return rs;
  }

  private Connection connectionWith(ResultSet rs) throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    when(conn.prepareStatement(anyString())).thenReturn(ps);
    when(ps.executeQuery()).thenReturn(rs);
    return conn;
  }

  private ResultSet countResultSet(long value) throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.next()).thenReturn(true, false);
    when(rs.getLong(1)).thenReturn(value);
    return rs;
  }

  /** Connection that returns a ResultSet directly from executeQuery() (no params). */
  private Connection connectionWithCountRs(ResultSet rs) throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    when(conn.prepareStatement(anyString())).thenReturn(ps);
    when(ps.executeQuery()).thenReturn(rs);
    return conn;
  }
}
