package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * PM1e — focused tests for the persistence half of
 * {@link PluginRegistry}: startup-seed from the DAO, write-through
 * on {@link PluginRegistry#setEnabled(String, boolean, String)},
 * sparse-table semantics (reset-to-default deletes the row), and
 * fail-soft DAO behaviour.
 *
 * <p>The tests bypass CDI by reflectively wiring a fake
 * {@link Instance} carrying an in-memory DAO fake. This mirrors
 * {@code PluginRegistryTest}'s posture but adds the override-persistence
 * dimension. The PM1a JAR-discovery path is not exercised here —
 * tests stage entries directly into the {@code entries} map (the
 * idiom established by {@code PluginRegistryDependencyTest}).
 */
class PluginRegistryPersistenceTest {

  private PluginRegistry registry;
  private FakeOverrideDao fakeDao;

  @BeforeEach
  void setUp() throws Exception {
    registry = new PluginRegistry();
    fakeDao = new FakeOverrideDao();
    setField(registry, "overrideDao", FakeInstance.of(fakeDao));
    setField(registry, "classpathScanEnabled", false);
  }

  // ─── seed-on-startup ────────────────────────────────────────────────────

  @Test
  void seedOverridesFromDao_populatesInMemoryCache() {
    fakeDao.rows.add(makeRow("unhide", false));
    fakeDao.rows.add(makeRow("kip", true));

    registry.seedOverridesFromDao();

    // Stage a couple of registered plugins so isEnabled() doesn't
    // bail on "unknown plugin id".
    registry.entries.put("unhide", makeEntry("unhide"));
    registry.entries.put("kip", makeEntry("kip"));

    assertThat(registry.isEnabled("unhide")).isFalse();
    assertThat(registry.isEnabled("kip")).isTrue();
  }

  @Test
  void seedOverridesFromDao_emptyTableLeavesCacheEmpty() {
    registry.seedOverridesFromDao();
    registry.entries.put("unhide", makeEntry("unhide"));
    // No row → no override → fall-through to deploy-time default (false, opt-in posture).
    assertThat(registry.isEnabled("unhide")).isFalse();
  }

  @Test
  void seedOverridesFromDao_isIdempotent() {
    fakeDao.rows.add(makeRow("unhide", false));
    registry.seedOverridesFromDao();
    registry.seedOverridesFromDao(); // should not double-up

    registry.entries.put("unhide", makeEntry("unhide"));
    assertThat(registry.isEnabled("unhide")).isFalse();
  }

  @Test
  void seedOverridesFromDao_failSoftOnDaoException() {
    // A DB outage at startup must not kneecap discovery.
    fakeDao.failOnRead = true;
    registry.seedOverridesFromDao(); // does not throw

    registry.entries.put("unhide", makeEntry("unhide"));
    // No override seeded → fall-through to deploy-time default (false, opt-in posture).
    assertThat(registry.isEnabled("unhide")).isFalse();
  }

  // ─── write-through ──────────────────────────────────────────────────────

  @Test
  void setEnabled_enableAwayFromDefaultPersistsRow() {
    // Default is false (opt-in); enabling creates an override row.
    registry.entries.put("unhide", makeEntry("unhide"));
    registry.setEnabled("unhide", true, "admin-1");

    // Row exists in the DAO with enabled=true.
    Optional<PluginRuntimeOverride> persisted = fakeDao.findByPluginId("unhide");
    assertThat(persisted).isPresent();
    assertThat(persisted.get().isEnabled()).isTrue();
    assertThat(persisted.get().getUpdatedBy()).isEqualTo("admin-1");
    assertThat(persisted.get().getUpdatedAt()).isNotNull();

    // In-memory cache mirrors the persisted state.
    assertThat(registry.isEnabled("unhide")).isTrue();
  }

  @Test
  void setEnabled_resetToDeployDefaultDeletesRow() {
    // First enable (persists a row, since default is false), then disable
    // back to the deploy-time default (false → row deleted).
    registry.entries.put("unhide", makeEntry("unhide"));
    registry.setEnabled("unhide", true, "admin-1");
    assertThat(fakeDao.rows).hasSize(1);

    registry.setEnabled("unhide", false, "admin-2");
    // Sparse-table invariant: disabling back to the deploy-time
    // default (false) deletes the row.
    assertThat(fakeDao.rows).isEmpty();
    // isEnabled falls back to the deploy-time default.
    assertThat(registry.isEnabled("unhide")).isFalse();
  }

  @Test
  void setEnabled_unknownPluginIdThrows() {
    org.junit.jupiter.api.Assertions.assertThrows(
      IllegalArgumentException.class,
      () -> registry.setEnabled("ghost", false, "admin-1")
    );
    // No DAO interaction either.
    assertThat(fakeDao.saves).isZero();
    assertThat(fakeDao.deletes).isZero();
  }

  @Test
  void setEnabled_nullActorSubFallsBackToAnonymous() {
    registry.entries.put("unhide", makeEntry("unhide"));
    // Enable is "away from default" (false) → persists a row.
    registry.setEnabled("unhide", true, null);

    PluginRuntimeOverride persisted = fakeDao.findByPluginId("unhide").orElseThrow();
    assertThat(persisted.getUpdatedBy()).isEqualTo("anonymous");
  }

  @Test
  void setEnabled_writeFailureKeepsInMemoryCacheCurrent() {
    registry.entries.put("unhide", makeEntry("unhide"));
    fakeDao.failOnWrite = true;
    // Enable is "away from default" (false) → tries to upsert → DAO throws.
    // The persistence layer is broken, but the in-memory override is
    // still in effect so the current JVM behaves correctly until the
    // next restart (which would then fall back to the deploy-time
    // default — operator can re-PATCH after the DB recovers).
    registry.setEnabled("unhide", true, "admin-1");
    assertThat(registry.isEnabled("unhide")).isTrue();
  }

  // ─── restart simulation ──────────────────────────────────────────────────

  @Test
  void enableSurvivesSimulatedRestart() {
    // PM1e core promise: an admin who enables a plugin once expects
    // it to stay enabled across a restart.
    registry.entries.put("unhide", makeEntry("unhide"));
    registry.setEnabled("unhide", true, "admin-1");

    // Simulate restart: fresh registry instance, same DAO state.
    PluginRegistry restarted = new PluginRegistry();
    try {
      setField(restarted, "overrideDao", FakeInstance.of(fakeDao));
      setField(restarted, "classpathScanEnabled", false);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    restarted.seedOverridesFromDao();
    restarted.entries.put("unhide", makeEntry("unhide"));

    assertThat(restarted.isEnabled("unhide")).isTrue();
  }

  @Test
  void twoArgSetEnabledDelegatesToThreeArg() {
    registry.entries.put("unhide", makeEntry("unhide"));
    // Enable is "away from default" (false) → persists a row.
    registry.setEnabled("unhide", true);
    // Persisted with the "anonymous" sentinel since no actor passed.
    assertThat(fakeDao.findByPluginId("unhide")).isPresent();
    assertThat(fakeDao.findByPluginId("unhide").get().getUpdatedBy()).isEqualTo("anonymous");
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private PluginEntry makeEntry(String id) {
    PluginManifest manifest = Mockito.mock(PluginManifest.class);
    Mockito.when(manifest.id()).thenReturn(id);
    Mockito.when(manifest.version()).thenReturn("1.0.0");
    Mockito.when(manifest.shepardCompatibility()).thenReturn(">=5.2.0,<6");
    return new PluginEntry(manifest, null);
  }

  private PluginRuntimeOverride makeRow(String id, boolean enabled) {
    PluginRuntimeOverride r = new PluginRuntimeOverride();
    r.setPluginId(id);
    r.setEnabled(enabled);
    r.setUpdatedBy("seed");
    r.setUpdatedAt(java.time.Instant.now());
    return r;
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    var field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  /**
   * In-memory fake for {@link PluginRuntimeOverrideDAO} — implements
   * the same surface the registry calls (no Neo4j, no Mockito stub
   * forest). Carries the rows as a list so the tests can inspect
   * insertion order if they care.
   */
  private static final class FakeOverrideDao extends PluginRuntimeOverrideDAO {

    final List<PluginRuntimeOverride> rows = new ArrayList<>();
    int saves;
    int deletes;
    boolean failOnRead;
    boolean failOnWrite;

    FakeOverrideDao() {
      // Skip the parent constructor's NeoConnector lookup — the
      // tests never touch the session.
    }

    @Override
    public List<PluginRuntimeOverride> findAllOverrides() {
      if (failOnRead) throw new RuntimeException("simulated DB outage");
      return new ArrayList<>(rows);
    }

    @Override
    public Optional<PluginRuntimeOverride> findByPluginId(String pluginId) {
      if (pluginId == null || pluginId.isBlank()) return Optional.empty();
      return rows.stream().filter(r -> pluginId.equals(r.getPluginId())).findFirst();
    }

    @Override
    public PluginRuntimeOverride save(PluginRuntimeOverride override) {
      if (failOnWrite) throw new RuntimeException("simulated DB outage");
      saves++;
      // Upsert by pluginId.
      rows.removeIf(r -> override.getPluginId().equals(r.getPluginId()));
      if (override.getAppId() == null) {
        override.setAppId("appid-" + override.getPluginId());
      }
      rows.add(override);
      return override;
    }

    @Override
    public boolean deleteByPluginId(String pluginId) {
      if (failOnWrite) throw new RuntimeException("simulated DB outage");
      deletes++;
      return rows.removeIf(r -> pluginId.equals(r.getPluginId()));
    }
  }

  /**
   * Minimal CDI {@link Instance} fake — enough for the registry's
   * {@code isUnsatisfied()} / {@code get()} call sites.
   */
  static final class FakeInstance<T> implements Instance<T> {

    private final T value;

    private FakeInstance(T value) {
      this.value = value;
    }

    static <T> Instance<T> of(T value) {
      return new FakeInstance<>(value);
    }

    @Override
    public T get() {
      return value;
    }

    @Override
    public boolean isUnsatisfied() {
      return value == null;
    }

    @Override
    public boolean isAmbiguous() {
      return false;
    }

    @Override
    public boolean isResolvable() {
      return value != null;
    }

    @Override
    public java.util.Iterator<T> iterator() {
      return value == null ? java.util.Collections.emptyIterator() : List.of(value).iterator();
    }

    @Override
    public Instance<T> select(java.lang.annotation.Annotation... qualifiers) {
      return this;
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {
      @SuppressWarnings("unchecked")
      Instance<U> cast = (Instance<U>) this;
      return cast;
    }

    @Override
    public <U extends T> Instance<U> select(
      jakarta.enterprise.util.TypeLiteral<U> subtype,
      java.lang.annotation.Annotation... qualifiers
    ) {
      @SuppressWarnings("unchecked")
      Instance<U> cast = (Instance<U>) this;
      return cast;
    }

    @Override
    public void destroy(T instance) {
      // no-op
    }

    @Override
    public Handle<T> getHandle() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<? extends Handle<T>> handles() {
      return java.util.Collections.emptyList();
    }

    // Silence unused warning on the fields some Mockito harnesses keep.
    @SuppressWarnings("unused")
    private static final Map<String, Object> _placeholders = new HashMap<>();
  }
}
