package de.dlr.shepard.common.identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.NotFoundException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

public class EntityIdResolverTest {

  @Mock
  private Session session;

  @Mock
  private Result resolveLongResult;

  @Mock
  private Result resolveAppIdResult;

  private TestableResolver resolver;

  /**
   * Inject a stub {@link Session} into {@link EntityIdResolver} via the
   * protected {@code session()} seam so the resolver runs without a real
   * Neo4j connection.
   */
  private static class TestableResolver extends EntityIdResolver {

    private final Session stubSession;

    TestableResolver(Session stubSession) {
      this.stubSession = stubSession;
    }

    @Override
    protected Session session() {
      return stubSession;
    }
  }

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    resolver = new TestableResolver(session);
  }

  @Test
  public void resolveAppId_returnsAppIdForKnownOgmId() {
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(Map.of("appId", "abc-1")).iterator();
    when(resolveAppIdResult.iterator()).thenReturn(iter);
    when(session.query(eq("MATCH (e) WHERE id(e) = $ogmId RETURN e.appId AS appId LIMIT 1"), eq(Map.of("ogmId", 7L))))
      .thenReturn(resolveAppIdResult);

    assertThat(resolver.resolveAppId(7L)).isEqualTo("abc-1");
  }

  @Test
  public void resolveAppId_isMemoisedRequestScoped() {
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(Map.of("appId", "abc-1")).iterator();
    when(resolveAppIdResult.iterator()).thenReturn(iter);
    when(session.query(eq("MATCH (e) WHERE id(e) = $ogmId RETURN e.appId AS appId LIMIT 1"), eq(Map.of("ogmId", 7L))))
      .thenReturn(resolveAppIdResult);

    assertThat(resolver.resolveAppId(7L)).isEqualTo("abc-1");
    assertThat(resolver.resolveAppId(7L)).isEqualTo("abc-1");
    // Second call must reuse the memo, not re-query.
    verify(session, times(1)).query(
      eq("MATCH (e) WHERE id(e) = $ogmId RETURN e.appId AS appId LIMIT 1"),
      eq(Map.of("ogmId", 7L))
    );
  }

  @Test
  public void resolveAppId_notFound_whenNoNode() {
    when(resolveAppIdResult.iterator()).thenReturn(List.<Map<String, Object>>of().iterator());
    when(session.query(eq("MATCH (e) WHERE id(e) = $ogmId RETURN e.appId AS appId LIMIT 1"), eq(Map.of("ogmId", 99L))))
      .thenReturn(resolveAppIdResult);

    assertThatThrownBy(() -> resolver.resolveAppId(99L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  public void resolveAppId_notFound_whenAppIdNull() {
    Iterator<Map<String, Object>> singletonNullAppId = List.<Map<String, Object>>of(
      java.util.Collections.singletonMap("appId", null)
    ).iterator();
    when(resolveAppIdResult.iterator()).thenReturn(singletonNullAppId);
    when(session.query(eq("MATCH (e) WHERE id(e) = $ogmId RETURN e.appId AS appId LIMIT 1"), eq(Map.of("ogmId", 88L))))
      .thenReturn(resolveAppIdResult);

    assertThatThrownBy(() -> resolver.resolveAppId(88L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  public void resolveLong_returnsOgmIdForKnownAppId() {
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(Map.of("ogmId", 7L)).iterator();
    when(resolveLongResult.iterator()).thenReturn(iter);
    when(session.query(eq("MATCH (e {appId: $appId}) RETURN id(e) AS ogmId LIMIT 1"), eq(Map.of("appId", "abc-1"))))
      .thenReturn(resolveLongResult);

    assertThat(resolver.resolveLong("abc-1")).isEqualTo(7L);
  }

  @Test
  public void resolveLong_notFound_whenNoNode() {
    when(resolveLongResult.iterator()).thenReturn(List.<Map<String, Object>>of().iterator());
    when(session.query(eq("MATCH (e {appId: $appId}) RETURN id(e) AS ogmId LIMIT 1"), eq(Map.of("appId", "missing"))))
      .thenReturn(resolveLongResult);

    assertThatThrownBy(() -> resolver.resolveLong("missing")).isInstanceOf(NotFoundException.class);
  }

  @Test
  public void resolveLong_nullAppId_throws() {
    assertThatThrownBy(() -> resolver.resolveLong(null)).isInstanceOf(NotFoundException.class);
  }

  @Test
  public void roundTrip_resolveAppIdThenResolveLong_isMemoised() {
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(Map.of("appId", "rt-1")).iterator();
    when(resolveAppIdResult.iterator()).thenReturn(iter);
    when(session.query(eq("MATCH (e) WHERE id(e) = $ogmId RETURN e.appId AS appId LIMIT 1"), eq(Map.of("ogmId", 42L))))
      .thenReturn(resolveAppIdResult);

    assertThat(resolver.resolveAppId(42L)).isEqualTo("rt-1");
    // Reverse direction is now memoised — no extra Cypher round-trip.
    assertThat(resolver.resolveLong("rt-1")).isEqualTo(42L);
    verify(session, never()).query(
      eq("MATCH (e {appId: $appId}) RETURN id(e) AS ogmId LIMIT 1"),
      eq(Map.of("appId", "rt-1"))
    );
  }

  @Test
  public void primeForTesting_skipsCypherEntirely() {
    resolver.primeForTesting(99L, "primed-99");
    assertThat(resolver.resolveAppId(99L)).isEqualTo("primed-99");
    assertThat(resolver.resolveLong("primed-99")).isEqualTo(99L);
    verify(session, never()).query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap());
  }
}
