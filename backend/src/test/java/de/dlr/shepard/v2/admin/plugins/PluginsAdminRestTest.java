package de.dlr.shepard.v2.admin.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginEntry;
import de.dlr.shepard.plugin.PluginManifest;
import de.dlr.shepard.plugin.PluginRegistry;
import de.dlr.shepard.v2.admin.plugins.io.PluginEntryIO;
import de.dlr.shepard.v2.admin.plugins.io.PluginListIO;
import de.dlr.shepard.v2.admin.plugins.io.PluginPatchIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * PM1b — unit tests for {@link PluginsAdminRest}. Mocks the
 * {@link PluginRegistry} so the test exercises only the resource
 * wiring (JSON shape, role guard, RFC 7807 problem envelopes,
 * RFC 7396 merge-patch semantics).
 *
 * <p>Mirrors {@code AdminFeaturesRestTest} — same Mockito + manual
 * field injection idiom, since neither resource needs the Quarkus
 * harness.
 */
class PluginsAdminRestTest {

  @Mock
  PluginRegistry registry;

  PluginsAdminRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new PluginsAdminRest();
    resource.registry = registry;
  }

  // ─── annotations / wiring ────────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = PluginsAdminRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "PluginsAdminRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals("instance-admin", gate.value()[0]);
  }

  @Test
  void classCarriesV2AdminPluginsPath() {
    Path pathAnno = PluginsAdminRest.class.getAnnotation(Path.class);
    assertNotNull(pathAnno);
    assertEquals("/v2/admin/plugins", pathAnno.value());
  }

  // ─── GET /v2/admin/plugins ──────────────────────────────────────────────

  @Test
  void listEmptyReturns200WithEmptyArray() {
    Mockito.when(registry.list()).thenReturn(List.of());

    Response r = resource.list();

    assertEquals(200, r.getStatus());
    PluginListIO body = (PluginListIO) r.getEntity();
    assertNotNull(body);
    assertNotNull(body.plugins());
    assertTrue(body.plugins().isEmpty());
  }

  @Test
  void listPopulatedReturnsAllRows() {
    PluginEntry unhide = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, false);
    PluginEntry hdf = newEntry(
      "hdf-hsds",
      "0.3.0",
      ">=5.0.0,<6",
      Paths.get("/deployments/plugins/shepard-plugin-hdf-hsds-0.3.0.jar"),
      true
    );
    Mockito.when(registry.list()).thenReturn(List.of(unhide, hdf));
    Mockito.when(registry.isEnabled("unhide")).thenReturn(false);
    Mockito.when(registry.isEnabled("hdf-hsds")).thenReturn(true);

    Response r = resource.list();

    assertEquals(200, r.getStatus());
    PluginListIO body = (PluginListIO) r.getEntity();
    assertEquals(2, body.plugins().size());

    PluginEntryIO unhideRow = body.plugins().get(0);
    assertEquals("unhide", unhideRow.id());
    assertEquals("1.0.0", unhideRow.version());
    assertEquals(">=5.2.0,<6", unhideRow.shepardCompatibility());
    assertFalse(unhideRow.enabled(), "registry.isEnabled returned false → enabled=false in IO");
    assertNull(unhideRow.sourcePath(), "build-classpath plugin surfaces sourcePath=null");

    PluginEntryIO hdfRow = body.plugins().get(1);
    assertEquals("hdf-hsds", hdfRow.id());
    assertTrue(hdfRow.enabled());
    assertTrue(hdfRow.sourcePath().endsWith("shepard-plugin-hdf-hsds-0.3.0.jar"));
    assertNotNull(hdfRow.registeredAt());
  }

  // ─── PATCH happy paths ──────────────────────────────────────────────────

  @Test
  void patchEnableFlipsDisabledPlugin() {
    PluginEntry unhide = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, false);
    Mockito.when(registry.get("unhide")).thenReturn(Optional.of(unhide));
    // Before the flip, the registry reports DISABLED; we use an
    // AtomicBoolean to flip-through to true on setEnabled, mimicking
    // the real registry's in-memory override.
    AtomicBoolean effective = new AtomicBoolean(false);
    Mockito.when(registry.isEnabled("unhide")).thenAnswer(inv -> effective.get());
    Mockito.when(registry.setEnabled("unhide", true)).thenAnswer(inv -> {
      effective.set(true);
      return true;
    });

    PluginPatchIO body = new PluginPatchIO();
    body.setEnabled(Boolean.TRUE);

    Response r = resource.patch("unhide", body);

    assertEquals(200, r.getStatus());
    PluginEntryIO io = (PluginEntryIO) r.getEntity();
    assertEquals("unhide", io.id());
    assertTrue(io.enabled(), "after PATCH the IO reflects the flipped state");
    Mockito.verify(registry).setEnabled("unhide", true);
  }

  @Test
  void patchDisableFlipsEnabledPlugin() {
    PluginEntry uh = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, true);
    Mockito.when(registry.get("unhide")).thenReturn(Optional.of(uh));
    AtomicBoolean effective = new AtomicBoolean(true);
    Mockito.when(registry.isEnabled("unhide")).thenAnswer(inv -> effective.get());
    Mockito.when(registry.setEnabled("unhide", false)).thenAnswer(inv -> {
      effective.set(false);
      return false;
    });

    PluginPatchIO body = new PluginPatchIO();
    body.setEnabled(Boolean.FALSE);

    Response r = resource.patch("unhide", body);

    assertEquals(200, r.getStatus());
    PluginEntryIO io = (PluginEntryIO) r.getEntity();
    assertFalse(io.enabled());
    Mockito.verify(registry).setEnabled("unhide", false);
  }

  @Test
  void patchNoOpWhenAlreadyInTargetState() {
    PluginEntry uh = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, true);
    Mockito.when(registry.get("unhide")).thenReturn(Optional.of(uh));
    Mockito.when(registry.isEnabled("unhide")).thenReturn(true);

    PluginPatchIO body = new PluginPatchIO();
    body.setEnabled(Boolean.TRUE); // already true → no flip

    Response r = resource.patch("unhide", body);

    assertEquals(200, r.getStatus());
    PluginEntryIO io = (PluginEntryIO) r.getEntity();
    assertTrue(io.enabled());
    Mockito.verify(registry, Mockito.never()).setEnabled(Mockito.anyString(), Mockito.anyBoolean());
  }

  @Test
  void patchWithEmptyBodyReturnsCurrentEntry() {
    PluginEntry uh = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, true);
    Mockito.when(registry.get("unhide")).thenReturn(Optional.of(uh));
    Mockito.when(registry.isEnabled("unhide")).thenReturn(true);

    Response r = resource.patch("unhide", new PluginPatchIO());

    assertEquals(200, r.getStatus());
    PluginEntryIO io = (PluginEntryIO) r.getEntity();
    assertEquals("unhide", io.id());
    Mockito.verify(registry, Mockito.never()).setEnabled(Mockito.anyString(), Mockito.anyBoolean());
  }

  @Test
  void patchWithNullBodyReturnsCurrentEntry() {
    PluginEntry uh = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, true);
    Mockito.when(registry.get("unhide")).thenReturn(Optional.of(uh));
    Mockito.when(registry.isEnabled("unhide")).thenReturn(true);

    Response r = resource.patch("unhide", null);

    assertEquals(200, r.getStatus());
    assertInstanceOf(PluginEntryIO.class, r.getEntity());
  }

  // ─── PATCH error paths ──────────────────────────────────────────────────

  @Test
  void patchUnknownIdReturns404ProblemJson() {
    Mockito.when(registry.get("nonexistent")).thenReturn(Optional.empty());

    PluginPatchIO body = new PluginPatchIO();
    body.setEnabled(Boolean.TRUE);

    Response r = resource.patch("nonexistent", body);

    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson envelope = (ProblemJson) r.getEntity();
    assertEquals(PluginsAdminRest.PROBLEM_TYPE_NOT_FOUND, envelope.type());
    assertEquals(404, envelope.status());
    assertTrue(envelope.detail().contains("nonexistent"), "detail cites the missing id");
  }

  @Test
  void patchReadOnlyFieldReturns400ProblemJson() {
    // Caller mentions a non-existent field — captured by
    // PluginPatchIO.captureUnknown via @JsonAnySetter.
    PluginPatchIO body = new PluginPatchIO();
    body.captureUnknown("shepardCompatibility", ">=99.0.0");
    // No need to stub registry.get — the resource short-circuits on
    // the unknown-field check before touching the registry.

    Response r = resource.patch("unhide", body);

    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson envelope = (ProblemJson) r.getEntity();
    assertEquals(PluginsAdminRest.PROBLEM_TYPE_READ_ONLY_FIELD, envelope.type());
    assertEquals(400, envelope.status());
    assertTrue(envelope.detail().contains("shepardCompatibility"), "detail cites the offending field");
    Mockito.verifyNoInteractions(registry);
  }

  @Test
  void patchReadOnlyFieldChecksRunBeforeUnknownIdLookup() {
    // Defensive: if both the id is unknown AND the body carries an
    // unpatchable field, the read-only-field 400 wins — that's the
    // more informative error to surface.
    PluginPatchIO body = new PluginPatchIO();
    body.setEnabled(Boolean.TRUE);
    body.captureUnknown("malicious", "value");

    Response r = resource.patch("nonexistent", body);

    assertEquals(400, r.getStatus());
    Mockito.verify(registry, Mockito.never()).get(Mockito.anyString());
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private PluginEntry newEntry(
    String id,
    String version,
    String compat,
    java.nio.file.Path jarPath,
    boolean markEnabled
  ) {
    PluginEntry entry = new PluginEntry(stubManifest(id, version, compat), jarPath, Instant.parse("2026-05-13T05:00:00Z"));
    if (markEnabled) {
      // We can't call the package-private markEnabled() from this
      // package, so flip via the same mechanism the registry would:
      // a fresh entry starts at DISCOVERED. For the read-side IO
      // serialisation tests we don't need ENABLED specifically —
      // PluginEntryIO.from carries whatever state the entry is in.
    }
    return entry;
  }

  private PluginManifest stubManifest(String id, String version, String compat) {
    return new PluginManifest() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public String version() {
        return version;
      }

      @Override
      public String shepardCompatibility() {
        return compat;
      }

      @Override
      public void onRegister(PluginContext ctx) {
        // no-op for tests
      }
    };
  }
}
