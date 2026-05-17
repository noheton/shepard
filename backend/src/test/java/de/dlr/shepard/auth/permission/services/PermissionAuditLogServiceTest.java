package de.dlr.shepard.auth.permission.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agroal.api.AgroalDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * F3 — unit tests for {@link PermissionAuditLogService}.
 *
 * <p>Covers the two contract invariants:
 * <ol>
 *   <li>A successful call inserts one row.</li>
 *   <li>A JDBC failure never propagates — {@link PermissionAuditLogService#log} is fire-and-forget.</li>
 * </ol>
 */
class PermissionAuditLogServiceTest {

  @Mock
  AgroalDataSource defaultDataSource;

  @InjectMocks
  PermissionAuditLogService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void log_happyPath_executesInsert() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    when(defaultDataSource.getConnection()).thenReturn(conn);
    when(conn.prepareStatement(anyString())).thenReturn(ps);
    when(ps.executeUpdate()).thenReturn(1);

    service.log("app-id-123", "Collection", "alice", "GRANT", "{\"detail\":\"ok\"}");

    verify(ps).setString(2, "app-id-123");
    verify(ps).setString(3, "Collection");
    verify(ps).setString(4, "alice");
    verify(ps).setString(5, "GRANT");
    verify(ps).setString(6, "{\"detail\":\"ok\"}");
    verify(ps).executeUpdate();
  }

  @Test
  void log_nullableFieldsAccepted() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    when(defaultDataSource.getConnection()).thenReturn(conn);
    when(conn.prepareStatement(anyString())).thenReturn(ps);
    when(ps.executeUpdate()).thenReturn(1);

    // entityKind, actorUsername, detailJson are all nullable
    service.log("app-id-456", null, null, "UPDATE", null);

    verify(ps).setString(2, "app-id-456");
    verify(ps).setString(3, null);
    verify(ps).setString(4, null);
    verify(ps).setString(5, "UPDATE");
    verify(ps).setString(6, null);
    verify(ps).executeUpdate();
  }

  @Test
  void log_jdbcThrows_neverPropagates() throws Exception {
    when(defaultDataSource.getConnection()).thenThrow(new SQLException("connection pool exhausted"));

    // Must not throw — fire-and-forget contract
    assertDoesNotThrow(() -> service.log("app-id-789", "DataObject", "bob", "REVOKE", null));
  }

  @Test
  void log_prepareStatementThrows_neverPropagates() throws Exception {
    Connection conn = mock(Connection.class);
    when(defaultDataSource.getConnection()).thenReturn(conn);
    when(conn.prepareStatement(anyString())).thenThrow(new SQLException("syntax error"));

    assertDoesNotThrow(() -> service.log("app-id-001", "Collection", "carol", "GRANT", null));
  }

  @Test
  void log_executeUpdateThrows_neverPropagates() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    when(defaultDataSource.getConnection()).thenReturn(conn);
    when(conn.prepareStatement(anyString())).thenReturn(ps);
    when(ps.executeUpdate()).thenThrow(new SQLException("table missing"));

    assertDoesNotThrow(() -> service.log("app-id-002", null, "dave", "UPDATE", "{}"));
  }

  @Test
  void log_nullEntityAppId_skipsInsertWithoutThrowing() throws Exception {
    // null entityAppId is an invalid call — should warn and return without touching the DB
    assertDoesNotThrow(() -> service.log(null, "Collection", "eve", "GRANT", null));
  }

  @Test
  void log_nullAction_skipsInsertWithoutThrowing() throws Exception {
    assertDoesNotThrow(() -> service.log("app-id-003", "Collection", "frank", null, null));
  }
}
