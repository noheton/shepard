package de.dlr.shepard.v2.admin.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginDependency;
import de.dlr.shepard.plugin.PluginEntry;
import de.dlr.shepard.plugin.PluginManifest;
import de.dlr.shepard.plugin.PluginRegistry;
import de.dlr.shepard.v2.admin.plugins.io.PluginDependencyIO;
import de.dlr.shepard.v2.admin.plugins.io.PluginEntryIO;
import de.dlr.shepard.v2.admin.plugins.io.PluginListIO;
import de.dlr.shepard.v2.admin.plugins.io.PluginPatchIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.net.URI;
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

  @Test
  void listEnrichedManifest_surfacesPm1cFields() {
    // A manifest that overrides every PM1c default — title / description /
    // homepage / repository / licence / dependencies all visible in the IO.
    PluginEntry enriched = new PluginEntry(richManifest(), null, Instant.parse("2026-05-13T05:00:00Z"));
    Mockito.when(registry.list()).thenReturn(List.of(enriched));
    Mockito.when(registry.isEnabled("rich")).thenReturn(true);

    Response r = resource.list();

    assertEquals(200, r.getStatus());
    PluginListIO body = (PluginListIO) r.getEntity();
    assertEquals(1, body.plugins().size());
    PluginEntryIO io = body.plugins().get(0);

    assertEquals("Rich Plugin", io.title());
    assertEquals("A plugin with all the new PM1c metadata filled in.", io.description());
    assertEquals("https://example.com/rich", io.homepageUrl());
    assertEquals("https://github.com/example/rich", io.repositoryUrl());
    assertEquals("MIT", io.licence());
    assertNotNull(io.dependencies());
    assertEquals(1, io.dependencies().size());
    PluginDependencyIO dep = io.dependencies().get(0);
    assertEquals("base", dep.pluginId());
    assertEquals("[1.0,2.0)", dep.versionConstraint());
  }

  @Test
  void listBareManifest_collapsesBlankPm1cFieldsToNull() {
    // A bare manifest takes every PM1c default. title() defaults to id();
    // description() / licence() are empty strings; URLs are Optional.empty.
    // The IO collapses blank strings to null so they're omitted under
    // JsonInclude.NON_NULL — clients see only what the plugin declared.
    PluginEntry bare = new PluginEntry(
      stubManifest("bare", "0.1.0", ">=5.2.0,<6"),
      null,
      Instant.parse("2026-05-13T05:00:00Z")
    );
    Mockito.when(registry.list()).thenReturn(List.of(bare));
    Mockito.when(registry.isEnabled("bare")).thenReturn(true);

    Response r = resource.list();

    PluginEntryIO io = ((PluginListIO) r.getEntity()).plugins().get(0);
    assertEquals("bare", io.title(), "title defaults to id, surfaced non-null");
    assertNull(io.description(), "blank description collapses to null");
    assertNull(io.homepageUrl(), "Optional.empty collapses to null");
    assertNull(io.repositoryUrl());
    assertNull(io.licence(), "blank licence collapses to null");
    assertNotNull(io.dependencies(), "dependencies is always a list, never null");
    assertTrue(io.dependencies().isEmpty());
  }

  // ─── PATCH happy paths ──────────────────────────────────────────────────

  @Test
  void patchEnableFlipsDisabledPlugin() {
    PluginEntry unhide = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, false);
    Mockito.when(registry.get("unhide")).thenReturn(Optional.of(unhide));
    // Before the flip, the registry reports DISABLED; we use an
    // AtomicBoolean to flip-through to true on setEnabled, mimicking
    // the real registry's persistent-override behaviour.
    AtomicBoolean effective = new AtomicBoolean(false);
    Mockito.when(registry.isEnabled("unhide")).thenAnswer(inv -> effective.get());
    // PM1e — the resource calls the 3-arg setEnabled(id, enabled, actorSub).
    Mockito.when(registry.setEnabled(Mockito.eq("unhide"), Mockito.eq(true), Mockito.any())).thenAnswer(inv -> {
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
    Mockito.verify(registry).setEnabled(Mockito.eq("unhide"), Mockito.eq(true), Mockito.any());
  }

  @Test
  void patchDisableFlipsEnabledPlugin() {
    PluginEntry uh = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, true);
    Mockito.when(registry.get("unhide")).thenReturn(Optional.of(uh));
    AtomicBoolean effective = new AtomicBoolean(true);
    Mockito.when(registry.isEnabled("unhide")).thenAnswer(inv -> effective.get());
    Mockito.when(registry.setEnabled(Mockito.eq("unhide"), Mockito.eq(false), Mockito.any())).thenAnswer(inv -> {
      effective.set(false);
      return false;
    });

    PluginPatchIO body = new PluginPatchIO();
    body.setEnabled(Boolean.FALSE);

    Response r = resource.patch("unhide", body);

    assertEquals(200, r.getStatus());
    PluginEntryIO io = (PluginEntryIO) r.getEntity();
    assertFalse(io.enabled());
    Mockito.verify(registry).setEnabled(Mockito.eq("unhide"), Mockito.eq(false), Mockito.any());
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
    Mockito.verify(registry, Mockito.never()).setEnabled(Mockito.anyString(), Mockito.anyBoolean(), Mockito.any());
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
    Mockito.verify(registry, Mockito.never()).setEnabled(Mockito.anyString(), Mockito.anyBoolean(), Mockito.any());
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

  // ─── PM1e — actor capture from SecurityContext ──────────────────────────

  @Test
  void patchPropagatesCallerSubFromSecurityContext() {
    // PM1e — the resource resolves the admin's sub from the
    // SecurityContext and passes it to the 3-arg setEnabled so the
    // persisted override row carries `updatedBy=<sub>`.
    PluginEntry uh = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, true);
    Mockito.when(registry.get("unhide")).thenReturn(Optional.of(uh));
    AtomicBoolean effective = new AtomicBoolean(true);
    Mockito.when(registry.isEnabled("unhide")).thenAnswer(inv -> effective.get());
    Mockito.when(registry.setEnabled(Mockito.eq("unhide"), Mockito.eq(false), Mockito.eq("alice")))
      .thenAnswer(inv -> {
        effective.set(false);
        return false;
      });

    jakarta.ws.rs.core.SecurityContext sc = Mockito.mock(jakarta.ws.rs.core.SecurityContext.class);
    java.security.Principal principal = Mockito.mock(java.security.Principal.class);
    Mockito.when(principal.getName()).thenReturn("alice");
    Mockito.when(sc.getUserPrincipal()).thenReturn(principal);

    PluginPatchIO body = new PluginPatchIO();
    body.setEnabled(Boolean.FALSE);

    Response r = resource.patch("unhide", body, sc);

    assertEquals(200, r.getStatus());
    Mockito.verify(registry).setEnabled("unhide", false, "alice");
  }

  @Test
  void patchPassesNullActorWhenPrincipalAbsent() {
    // No principal on the SecurityContext → resource passes null,
    // registry persists "anonymous".
    PluginEntry uh = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, true);
    Mockito.when(registry.get("unhide")).thenReturn(Optional.of(uh));
    AtomicBoolean effective = new AtomicBoolean(true);
    Mockito.when(registry.isEnabled("unhide")).thenAnswer(inv -> effective.get());
    Mockito.when(registry.setEnabled(Mockito.eq("unhide"), Mockito.eq(false), Mockito.isNull()))
      .thenAnswer(inv -> {
        effective.set(false);
        return false;
      });

    jakarta.ws.rs.core.SecurityContext sc = Mockito.mock(jakarta.ws.rs.core.SecurityContext.class);
    Mockito.when(sc.getUserPrincipal()).thenReturn(null);

    PluginPatchIO body = new PluginPatchIO();
    body.setEnabled(Boolean.FALSE);

    Response r = resource.patch("unhide", body, sc);

    assertEquals(200, r.getStatus());
    Mockito.verify(registry).setEnabled("unhide", false, null);
  }

  @Test
  void patchPassesNullActorWhenPrincipalNameIsBlank() {
    PluginEntry uh = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, true);
    Mockito.when(registry.get("unhide")).thenReturn(Optional.of(uh));
    AtomicBoolean effective = new AtomicBoolean(true);
    Mockito.when(registry.isEnabled("unhide")).thenAnswer(inv -> effective.get());
    Mockito.when(registry.setEnabled(Mockito.eq("unhide"), Mockito.eq(false), Mockito.isNull()))
      .thenAnswer(inv -> {
        effective.set(false);
        return false;
      });

    jakarta.ws.rs.core.SecurityContext sc = Mockito.mock(jakarta.ws.rs.core.SecurityContext.class);
    java.security.Principal principal = Mockito.mock(java.security.Principal.class);
    Mockito.when(principal.getName()).thenReturn("   ");
    Mockito.when(sc.getUserPrincipal()).thenReturn(principal);

    PluginPatchIO body = new PluginPatchIO();
    body.setEnabled(Boolean.FALSE);

    Response r = resource.patch("unhide", body, sc);

    assertEquals(200, r.getStatus());
    Mockito.verify(registry).setEnabled("unhide", false, null);
  }

  @Test
  void twoArgPatchOverloadStillWorks_passingNullSecurityContext() {
    // The 2-arg patch(id, body) overload exists for source
    // compatibility with the PM1b tests; it delegates to the 3-arg
    // path with a null SecurityContext (→ null actor).
    PluginEntry uh = newEntry("unhide", "1.0.0", ">=5.2.0,<6", null, true);
    Mockito.when(registry.get("unhide")).thenReturn(Optional.of(uh));
    AtomicBoolean effective = new AtomicBoolean(true);
    Mockito.when(registry.isEnabled("unhide")).thenAnswer(inv -> effective.get());
    Mockito.when(registry.setEnabled(Mockito.eq("unhide"), Mockito.eq(false), Mockito.isNull()))
      .thenAnswer(inv -> {
        effective.set(false);
        return false;
      });

    PluginPatchIO body = new PluginPatchIO();
    body.setEnabled(Boolean.FALSE);

    Response r = resource.patch("unhide", body);

    assertEquals(200, r.getStatus());
    Mockito.verify(registry).setEnabled("unhide", false, null);
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

  /** PM1c — exercises every overridable default method. */
  private PluginManifest richManifest() {
    return new PluginManifest() {
      @Override
      public String id() {
        return "rich";
      }

      @Override
      public String version() {
        return "2.5.0";
      }

      @Override
      public String shepardCompatibility() {
        return ">=5.2.0,<6";
      }

      @Override
      public String title() {
        return "Rich Plugin";
      }

      @Override
      public String description() {
        return "A plugin with all the new PM1c metadata filled in.";
      }

      @Override
      public Optional<URI> homepageUrl() {
        return Optional.of(URI.create("https://example.com/rich"));
      }

      @Override
      public Optional<URI> repositoryUrl() {
        return Optional.of(URI.create("https://github.com/example/rich"));
      }

      @Override
      public String licence() {
        return "MIT";
      }

      @Override
      public List<PluginDependency> dependencies() {
        return List.of(new PluginDependency("base", "[1.0,2.0)"));
      }
    };
  }
}
