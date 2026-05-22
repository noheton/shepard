package de.dlr.shepard.provenance.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.entities.InstanceConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class HmacChainServiceTest {

  @Mock
  InstanceConfigService instanceConfigService;

  @Mock
  ActivityDAO activityDAO;

  HmacChainService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new HmacChainService();
    service.instanceConfigService = instanceConfigService;
    service.activityDAO = activityDAO;

    var cfg = new InstanceConfig();
    cfg.setInstanceSecret("dGVzdC1zZWNyZXQ="); // base64("test-secret")
    cfg.setSecretVersion(1);
    when(instanceConfigService.current()).thenReturn(cfg);
  }

  // ─── stamp() ──────────────────────────────────────────────────────

  @Test
  void stampSetsAllChainFieldsOnFirstActivity() {
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(1))).thenReturn(List.of());

    Activity a = newActivity("CREATE", "alice", 1000L, 2000L);
    service.stamp(a);

    assertThat(a.getAuditHmac()).isNotNull().hasSize(64); // hex of 32 bytes
    assertThat(a.getAuditPrevHmac()).isNull(); // chain start
    assertThat(a.getSecretVersion()).isEqualTo(1);
  }

  @Test
  void stampChainsToLatestActivity() {
    Activity prior = newActivity("CREATE", "alice", 100L, 200L);
    prior.setAuditHmac("deadbeef".repeat(8));
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(1))).thenReturn(List.of(prior));

    Activity next = newActivity("UPDATE", "alice", 300L, 400L);
    service.stamp(next);

    assertThat(next.getAuditPrevHmac()).isEqualTo(prior.getAuditHmac());
    assertThat(next.getAuditHmac()).isNotNull();
  }

  @Test
  void stampIsBestEffortWhenSecretMissing() {
    var emptyCfg = new InstanceConfig();
    emptyCfg.setInstanceSecret(null);
    when(instanceConfigService.current()).thenReturn(emptyCfg);

    Activity a = newActivity("CREATE", "alice", 1L, 2L);
    service.stamp(a);

    assertThat(a.getAuditHmac()).isNull();
    assertThat(a.getSecretVersion()).isNull();
  }

  @Test
  void stampIsBestEffortOnDaoFailure() {
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(1)))
      .thenThrow(new RuntimeException("neo4j down"));

    Activity a = newActivity("CREATE", "alice", 1L, 2L);
    // Must not throw — the chain must never block the user write.
    service.stamp(a);

    assertThat(a.getAuditHmac()).isNull();
  }

  // ─── verify() ─────────────────────────────────────────────────────

  @Test
  void verifySucceedsOnUntamperedChain() {
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(1))).thenReturn(List.of());

    Activity a = newActivity("CREATE", "alice", 1L, 2L);
    service.stamp(a);

    boolean ok = service.verify(a, null, Map.of(1, "dGVzdC1zZWNyZXQ="));

    assertThat(ok).isTrue();
  }

  @Test
  void verifyFailsWhenChainPointerForged() {
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(1))).thenReturn(List.of());
    Activity a = newActivity("CREATE", "alice", 1L, 2L);
    service.stamp(a);
    a.setAuditPrevHmac("forged_pointer".repeat(4) + "f"); // 64 hex-ish chars

    boolean ok = service.verify(a, null, Map.of(1, "dGVzdC1zZWNyZXQ="));

    assertThat(ok).isFalse();
  }

  @Test
  void verifyFailsWhenDataMutated() {
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(1))).thenReturn(List.of());
    Activity a = newActivity("CREATE", "alice", 1L, 2L);
    service.stamp(a);

    // Tamper with a chained field after stamping.
    a.setAgentUsername("mallory");

    boolean ok = service.verify(a, null, Map.of(1, "dGVzdC1zZWNyZXQ="));

    assertThat(ok).isFalse();
  }

  @Test
  void verifyFailsWhenKeyVersionMissing() {
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(1))).thenReturn(List.of());
    Activity a = newActivity("CREATE", "alice", 1L, 2L);
    service.stamp(a);

    boolean ok = service.verify(a, null, Map.of(99, "other-key"));

    assertThat(ok).isFalse();
  }

  // ─── canonical() ──────────────────────────────────────────────────

  @Test
  void canonicalIsStable() {
    Activity a = newActivity("CREATE", "alice", 100L, 200L);
    String c1 = HmacChainService.canonical(a);
    String c2 = HmacChainService.canonical(a);

    assertThat(c1).isEqualTo(c2);
    assertThat(c1).contains("CREATE", "alice");
  }

  @Test
  void canonicalDiffersWhenChainedFieldChanges() {
    Activity a = newActivity("CREATE", "alice", 100L, 200L);
    Activity b = newActivity("CREATE", "mallory", 100L, 200L);

    assertThat(HmacChainService.canonical(a)).isNotEqualTo(HmacChainService.canonical(b));
  }

  // ─── constantTimeEq() ─────────────────────────────────────────────

  @Test
  void constantTimeEqHandlesEdgeCases() {
    assertThat(HmacChainService.constantTimeEq(null, "x")).isFalse();
    assertThat(HmacChainService.constantTimeEq("x", null)).isFalse();
    assertThat(HmacChainService.constantTimeEq("ab", "abc")).isFalse();
    assertThat(HmacChainService.constantTimeEq("abc", "abc")).isTrue();
    assertThat(HmacChainService.constantTimeEq("abc", "abd")).isFalse();
  }

  // ─── helpers ──────────────────────────────────────────────────────

  private Activity newActivity(String action, String user, long start, long end) {
    Activity a = new Activity();
    a.setActionKind(action);
    a.setAgentUsername(user);
    a.setStartedAtMillis(start);
    a.setEndedAtMillis(end);
    a.setMethod("POST");
    a.setPath("/v2/test");
    a.setStatus(200);
    a.setOriginInstance("test-instance");
    return a;
  }
}
