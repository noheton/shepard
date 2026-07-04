package de.dlr.shepard.context.collection.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import de.dlr.shepard.common.neo4j.NeoConnector;
import jakarta.ws.rs.WebApplicationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * #27-ARCHIVED — unit tests for {@link ArchiveStateGuard}. Stubs the
 * {@link NeoConnector} singleton with Mockito's {@code mockStatic} so the
 * guard can be exercised without a live Neo4j.
 */
public class ArchiveStateGuardTest {

  private final ArchiveStateGuard guard = new ArchiveStateGuard();

  private static Result resultWithStatus(String status) {
    Map<String, Object> row = new HashMap<>();
    row.put("s", status);
    Result r = mock(Result.class);
    when(r.queryResults()).thenReturn(List.of(row));
    return r;
  }

  private static Result emptyResult() {
    Result r = mock(Result.class);
    when(r.queryResults()).thenReturn(List.of());
    return r;
  }

  @Test
  public void assertCollectionNotArchived_archived_throws409() {
    Result r = resultWithStatus("ARCHIVED");
    NeoConnector connector = mock(NeoConnector.class);
    Session session = mock(Session.class);
    when(connector.getNeo4jSession()).thenReturn(session);
    when(session.query(anyString(), anyMap())).thenReturn(r);

    try (MockedStatic<NeoConnector> mocked = mockStatic(NeoConnector.class)) {
      mocked.when(NeoConnector::getInstance).thenReturn(connector);
      WebApplicationException ex = assertThrows(
        WebApplicationException.class,
        () -> guard.assertCollectionNotArchived(42L)
      );
      assertEquals(409, ex.getResponse().getStatus());
    }
  }

  @Test
  public void assertCollectionNotArchived_ready_passes() {
    Result r = resultWithStatus("READY");
    NeoConnector connector = mock(NeoConnector.class);
    Session session = mock(Session.class);
    when(connector.getNeo4jSession()).thenReturn(session);
    when(session.query(anyString(), anyMap())).thenReturn(r);

    try (MockedStatic<NeoConnector> mocked = mockStatic(NeoConnector.class)) {
      mocked.when(NeoConnector::getInstance).thenReturn(connector);
      guard.assertCollectionNotArchived(42L); // no throw
    }
  }

  @Test
  public void assertCollectionNotArchived_nullStatus_passes() {
    Result r = resultWithStatus(null);
    NeoConnector connector = mock(NeoConnector.class);
    Session session = mock(Session.class);
    when(connector.getNeo4jSession()).thenReturn(session);
    when(session.query(anyString(), anyMap())).thenReturn(r);

    try (MockedStatic<NeoConnector> mocked = mockStatic(NeoConnector.class)) {
      mocked.when(NeoConnector::getInstance).thenReturn(connector);
      guard.assertCollectionNotArchived(42L); // no throw — null is unblocked
    }
  }

  @Test
  public void assertCollectionNotArchived_missingNode_passes() {
    Result r = emptyResult();
    NeoConnector connector = mock(NeoConnector.class);
    Session session = mock(Session.class);
    when(connector.getNeo4jSession()).thenReturn(session);
    when(session.query(anyString(), anyMap())).thenReturn(r);

    try (MockedStatic<NeoConnector> mocked = mockStatic(NeoConnector.class)) {
      mocked.when(NeoConnector::getInstance).thenReturn(connector);
      guard.assertCollectionNotArchived(404L); // no throw — non-existent passes
    }
  }

  @Test
  public void assertContainerNotArchived_archived_throws409() {
    Result r = resultWithStatus("ARCHIVED");
    NeoConnector connector = mock(NeoConnector.class);
    Session session = mock(Session.class);
    when(connector.getNeo4jSession()).thenReturn(session);
    when(session.query(anyString(), anyMap())).thenReturn(r);

    try (MockedStatic<NeoConnector> mocked = mockStatic(NeoConnector.class)) {
      mocked.when(NeoConnector::getInstance).thenReturn(connector);
      WebApplicationException ex = assertThrows(
        WebApplicationException.class,
        () -> guard.assertContainerNotArchived(99L)
      );
      assertEquals(409, ex.getResponse().getStatus());
    }
  }

  @Test
  public void assertContainerNotArchived_draft_passes() {
    Result r = resultWithStatus("DRAFT");
    NeoConnector connector = mock(NeoConnector.class);
    Session session = mock(Session.class);
    when(connector.getNeo4jSession()).thenReturn(session);
    when(session.query(anyString(), anyMap())).thenReturn(r);

    try (MockedStatic<NeoConnector> mocked = mockStatic(NeoConnector.class)) {
      mocked.when(NeoConnector::getInstance).thenReturn(connector);
      guard.assertContainerNotArchived(99L); // no throw
    }
  }
}
