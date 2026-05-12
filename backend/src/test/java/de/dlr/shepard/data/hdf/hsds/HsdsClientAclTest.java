package de.dlr.shepard.data.hdf.hsds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A5b — coverage for the ACL methods added to {@link HsdsClient}.
 * Reuses the same loopback HTTP-server stub shape as
 * {@link HsdsClientTest} so the auth-header + path + method
 * assertions stay byte-identical with the A5a fixture.
 */
class HsdsClientAclTest {

  private HttpServer server;
  private List<String> capturedMethods;
  private List<String> capturedUris;
  private List<String> capturedBodies;
  private AtomicInteger nextStatus;
  private String nextBody;

  @BeforeEach
  void start() throws IOException {
    capturedMethods = new CopyOnWriteArrayList<>();
    capturedUris = new CopyOnWriteArrayList<>();
    capturedBodies = new CopyOnWriteArrayList<>();
    nextStatus = new AtomicInteger(200);
    // Default ACL stub: alice and bob hold ACEs (the bridge will delete bob, keep alice).
    nextBody = "{\"acls\":[{\"userName\":\"alice\"},{\"userName\":\"bob\"}]}";
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      capturedMethods.add(exchange.getRequestMethod());
      capturedUris.add(exchange.getRequestURI().toString());
      try (var is = exchange.getRequestBody()) {
        capturedBodies.add(new String(is.readAllBytes(), StandardCharsets.UTF_8));
      }
      byte[] body = nextBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(nextStatus.get(), body.length);
      try (var os = exchange.getResponseBody()) {
        os.write(body);
      }
    });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private HsdsClient client() {
    String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
    return new HsdsClient(endpoint, "admin", "secret", Duration.ofSeconds(5), HttpClient.newHttpClient());
  }

  // ─── getDomainAcl ───────────────────────────────────────────────────────

  @Test
  void getDomainAclReturnsBody() {
    nextStatus.set(200);
    nextBody = "{\"acls\":[{\"userName\":\"alice\"}]}";
    String body = client().getDomainAcl("/shepard/abc/");
    assertEquals("GET", capturedMethods.get(0));
    assertTrue(capturedUris.get(0).contains("/acls"));
    assertTrue(capturedUris.get(0).contains("domain=/shepard/abc/"));
    assertTrue(body.contains("alice"));
  }

  @Test
  void getDomainAclRaisesOn500() {
    nextStatus.set(500);
    nextBody = "boom";
    var ex = assertThrows(HsdsClient.HsdsException.class, () -> client().getDomainAcl("/shepard/abc/"));
    assertTrue(ex.getMessage().contains("HTTP 500"));
  }

  // ─── setDomainAcl ───────────────────────────────────────────────────────

  @Test
  void setDomainAclWritesPerPrincipalEntries() {
    nextStatus.set(200);
    nextBody = "{\"acls\":[]}";  // empty existing ACL — no DELETE pass

    client().setDomainAcl("/shepard/foo/", "alice", Set.of("bob"), Set.of("carol"), Set.of("dave"));

    // First call is GET (clear pass reads existing ACL). Then PUTs.
    long puts = capturedMethods.stream().filter("PUT"::equals).count();
    // owner + manager + writer + reader = 4 PUTs
    assertEquals(4, puts, "expected 4 PUT /acls/{user}, captured: " + capturedMethods);

    // Each PUT URI carries the right user.
    long putAlice = capturedUris.stream().filter(u -> u.startsWith("/acls/alice")).count();
    long putBob = capturedUris.stream().filter(u -> u.startsWith("/acls/bob")).count();
    long putCarol = capturedUris.stream().filter(u -> u.startsWith("/acls/carol")).count();
    long putDave = capturedUris.stream().filter(u -> u.startsWith("/acls/dave")).count();
    assertEquals(1, putAlice);
    assertEquals(1, putBob);
    assertEquals(1, putCarol);
    assertEquals(1, putDave);
  }

  @Test
  void setDomainAclMapsReaderToReadOnly() {
    nextStatus.set(200);
    nextBody = "{\"acls\":[]}";
    client().setDomainAcl("/shepard/foo/", "alice", Set.of("bob"), Set.of(), Set.of());

    // Find the PUT body for "bob" (the reader).
    String bobBody = null;
    for (int i = 0; i < capturedUris.size(); i++) {
      if (capturedUris.get(i).startsWith("/acls/bob") && "PUT".equals(capturedMethods.get(i))) {
        bobBody = capturedBodies.get(i);
        break;
      }
    }
    assertNotNull(bobBody, "Bob's PUT not captured");
    assertTrue(bobBody.contains("\"read\":true"));
    assertTrue(bobBody.contains("\"update\":false"));
    assertTrue(bobBody.contains("\"create\":false"));
    assertTrue(bobBody.contains("\"delete\":false"));
    assertTrue(bobBody.contains("\"readACL\":false"));
    assertTrue(bobBody.contains("\"updateACL\":false"));
  }

  @Test
  void setDomainAclMapsWriterToReadUpdateCreateDelete() {
    nextStatus.set(200);
    nextBody = "{\"acls\":[]}";
    client().setDomainAcl("/shepard/foo/", "alice", Set.of(), Set.of("carol"), Set.of());

    String carolBody = null;
    for (int i = 0; i < capturedUris.size(); i++) {
      if (capturedUris.get(i).startsWith("/acls/carol") && "PUT".equals(capturedMethods.get(i))) {
        carolBody = capturedBodies.get(i);
        break;
      }
    }
    assertNotNull(carolBody);
    assertTrue(carolBody.contains("\"read\":true"));
    assertTrue(carolBody.contains("\"update\":true"));
    assertTrue(carolBody.contains("\"create\":true"));
    assertTrue(carolBody.contains("\"delete\":true"));
    assertTrue(carolBody.contains("\"readACL\":false"));
    assertTrue(carolBody.contains("\"updateACL\":false"));
  }

  @Test
  void setDomainAclMapsManagerToFullPerms() {
    nextStatus.set(200);
    nextBody = "{\"acls\":[]}";
    client().setDomainAcl("/shepard/foo/", "alice", Set.of(), Set.of(), Set.of("dave"));

    String daveBody = null;
    for (int i = 0; i < capturedUris.size(); i++) {
      if (capturedUris.get(i).startsWith("/acls/dave") && "PUT".equals(capturedMethods.get(i))) {
        daveBody = capturedBodies.get(i);
        break;
      }
    }
    assertNotNull(daveBody);
    assertTrue(daveBody.contains("\"read\":true"));
    assertTrue(daveBody.contains("\"update\":true"));
    assertTrue(daveBody.contains("\"create\":true"));
    assertTrue(daveBody.contains("\"delete\":true"));
    assertTrue(daveBody.contains("\"readACL\":true"));
    assertTrue(daveBody.contains("\"updateACL\":true"));
  }

  @Test
  void setDomainAclMapsOwnerToFullPerms() {
    nextStatus.set(200);
    nextBody = "{\"acls\":[]}";
    client().setDomainAcl("/shepard/foo/", "alice", Set.of(), Set.of(), Set.of());

    String aliceBody = null;
    for (int i = 0; i < capturedUris.size(); i++) {
      if (capturedUris.get(i).startsWith("/acls/alice") && "PUT".equals(capturedMethods.get(i))) {
        aliceBody = capturedBodies.get(i);
        break;
      }
    }
    assertNotNull(aliceBody);
    assertTrue(aliceBody.contains("\"readACL\":true"));
    assertTrue(aliceBody.contains("\"updateACL\":true"));
  }

  @Test
  void setDomainAclSkipsBlankOwnerAndUsernames() {
    nextStatus.set(200);
    nextBody = "{\"acls\":[]}";
    // null owner, blank reader, valid manager — only the manager PUT should fire.
    client().setDomainAcl("/shepard/foo/", null, List.of("", "  "), List.of(), List.of("dave"));

    long puts = capturedMethods.stream().filter("PUT"::equals).count();
    assertEquals(1, puts, "expected only manager PUT, got methods: " + capturedMethods);
  }

  @Test
  void setDomainAclDeduplicatesOwnerAcrossLists() {
    nextStatus.set(200);
    nextBody = "{\"acls\":[]}";
    // alice is owner AND in writers — only one PUT for alice.
    client().setDomainAcl("/shepard/foo/", "alice", Set.of(), Set.of("alice"), Set.of());

    long alicePuts = capturedUris.stream().filter(u -> u.startsWith("/acls/alice")).count();
    assertEquals(1, alicePuts);
  }

  @Test
  void setDomainAclWhenAllPutsFailRaises() {
    nextStatus.set(500);
    nextBody = "boom";
    assertThrows(
      HsdsClient.HsdsException.class,
      () -> client().setDomainAcl("/shepard/foo/", "alice", Set.of(), Set.of(), Set.of())
    );
  }

  @Test
  void setDomainAclRejectsIllegalUsernames() {
    nextStatus.set(200);
    nextBody = "{\"acls\":[]}";
    assertThrows(
      IllegalArgumentException.class,
      () -> client().setDomainAcl("/shepard/foo/", "alice/etc/passwd", Set.of(), Set.of(), Set.of())
    );
  }

  // ─── clearDomainAcl ─────────────────────────────────────────────────────

  @Test
  void clearDomainAclDeletesExistingAces() {
    nextStatus.set(200);
    nextBody = "{\"acls\":[{\"userName\":\"alice\"},{\"userName\":\"bob\"}]}";

    client().clearDomainAcl("/shepard/foo/");

    long deletes = capturedMethods.stream().filter("DELETE"::equals).count();
    assertEquals(2, deletes, "expected DELETE for each ACE, captured: " + capturedMethods);
  }

  @Test
  void clearDomainAclSurvivesEmptyAcl() {
    nextStatus.set(200);
    nextBody = "{\"acls\":[]}";
    client().clearDomainAcl("/shepard/foo/");
    long deletes = capturedMethods.stream().filter("DELETE"::equals).count();
    assertEquals(0, deletes);
  }

  @Test
  void clearDomainAclSurvives404OnRead() {
    nextStatus.set(404);
    nextBody = "{}";
    client().clearDomainAcl("/shepard/foo/");
    long deletes = capturedMethods.stream().filter("DELETE"::equals).count();
    assertEquals(0, deletes);
  }

  // ─── parseAclUsernames ──────────────────────────────────────────────────

  @Test
  void parseAclUsernamesArrayShape() {
    var users = HsdsClient.parseAclUsernames("{\"acls\":[{\"userName\":\"alice\"},{\"userName\":\"bob\"}]}");
    assertTrue(users.contains("alice"));
    assertTrue(users.contains("bob"));
    assertEquals(2, users.size());
  }

  @Test
  void parseAclUsernamesObjectShape() {
    var users = HsdsClient.parseAclUsernames("{\"acls\":{\"alice\":{},\"bob\":{}}}");
    assertTrue(users.contains("alice"));
    assertTrue(users.contains("bob"));
  }

  @Test
  void parseAclUsernamesNullAndEmpty() {
    assertTrue(HsdsClient.parseAclUsernames(null).isEmpty());
    assertTrue(HsdsClient.parseAclUsernames("").isEmpty());
    assertTrue(HsdsClient.parseAclUsernames("not-json{").isEmpty());
  }

  // ─── fingerprintAcl ─────────────────────────────────────────────────────

  @Test
  void fingerprintAclStableUnderOrderAndDuplicates() {
    String a = HsdsClient.fingerprintAcl("alice", List.of("bob", "carol"), List.of("dave"), List.of("eve"));
    String b = HsdsClient.fingerprintAcl("alice", List.of("carol", "bob", "bob"), List.of("dave"), List.of("eve"));
    assertEquals(a, b, "fingerprint must ignore ordering + dupes");
  }

  @Test
  void fingerprintAclChangesOnSetChange() {
    String a = HsdsClient.fingerprintAcl("alice", List.of("bob"), List.of(), List.of());
    String b = HsdsClient.fingerprintAcl("alice", List.of("carol"), List.of(), List.of());
    assertFalse(a.equals(b), "fingerprint must change when readers change");
  }

  @Test
  void fingerprintAclHandlesNullAndBlankInputs() {
    String a = HsdsClient.fingerprintAcl(null, null, null, null);
    assertNotNull(a);
    String b = HsdsClient.fingerprintAcl("", List.of(""), List.of("  "), List.of());
    assertNotNull(b);
  }
}
