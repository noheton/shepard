package de.dlr.shepard.publish.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.publish.PublishableKind;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.publish.minter.MintRequest;
import de.dlr.shepard.publish.minter.MintResult;
import de.dlr.shepard.publish.minter.Minter;
import de.dlr.shepard.publish.minter.MinterNotInstalledException;
import de.dlr.shepard.publish.minter.MinterRegistry;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

class PublishServiceTest {

  private PublishService service;
  private MinterRegistry minterRegistry;
  private Minter minter;
  private PublicationDAO publicationDAO;
  private Session session;

  @BeforeEach
  void setUp() {
    minterRegistry = mock(MinterRegistry.class);
    minter = mock(Minter.class);
    publicationDAO = mock(PublicationDAO.class);
    session = mock(Session.class);

    when(minterRegistry.activeMinter()).thenReturn(Optional.of(minter));
    when(minter.id()).thenReturn("local");

    service = new PublishService() {
      @Override
      Session session() {
        return session;
      }
    };
    service.minterRegistry = minterRegistry;
    service.publicationDAO = publicationDAO;
  }

  /** Stub the verifyEntityKind + buildMetadata Cypher passes. */
  private void primeEntityExists() {
    Result kindResult = mock(Result.class);
    Iterator<Map<String, Object>> kindIter = List.<Map<String, Object>>of(Map.of("appId", "01HF-A")).iterator();
    when(kindResult.iterator()).thenReturn(kindIter);

    Result metaResult = mock(Result.class);
    Iterator<Map<String, Object>> metaIter = List.<Map<String, Object>>of(
      Map.of(
        "name", "TR-004",
        "createdAt", 1_700_000_000_000L,
        "updatedAt", 1_700_000_100_000L,
        "createdByUsername", "alice"
      )
    ).iterator();
    when(metaResult.iterator()).thenReturn(metaIter);

    when(session.query(anyString(), any(Map.class))).thenReturn(kindResult).thenReturn(metaResult);
  }

  @Test
  void publishesFreshWhenEntityHasNoPriorPublication() {
    primeEntityExists();
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(List.of());
    when(publicationDAO.findLatestVersionNumber("01HF-A")).thenReturn(0);
    when(minter.mint(any(MintRequest.class))).thenReturn(
      new MintResult("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1", Instant.ofEpochMilli(1_747_000_000_000L), "local")
    );
    when(publicationDAO.attachToEntity(any(Publication.class), eq("01HF-A"))).thenAnswer(inv -> {
      Publication p = inv.getArgument(0);
      p.setAppId("pub-app-id-1");
      return p;
    });

    PublishService.PublishOutcome outcome = service.publish(
      PublishableKind.DATA_OBJECTS,
      "01HF-A",
      "https://shepard.example/v2/data-objects/01HF-A",
      "alice",
      false
    );

    assertTrue(outcome.fresh());
    assertEquals("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1", outcome.publication().getPid());
    assertEquals("alice", outcome.publication().getPublishedBy());
    assertEquals("data-objects", outcome.publication().getEntityKind());
    assertEquals("01HF-A", outcome.publication().getEntityAppId());
    assertEquals("local", outcome.publication().getMinterId());
    assertEquals(Integer.valueOf(1), outcome.publication().getVersionNumber());
    verify(publicationDAO).attachToEntity(any(Publication.class), eq("01HF-A"));
  }

  @Test
  void idempotentPublishReturnsExistingWithoutMinting() {
    primeEntityExists();
    Publication existing = new Publication();
    existing.setAppId("pub-app-id-existing");
    existing.setPid("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1");
    existing.setMintedAt(1_700_000_000_000L);
    existing.setMinterId("local");
    existing.setEntityAppId("01HF-A");
    existing.setEntityKind("data-objects");
    existing.setPublishedBy("alice");
    existing.setVersionNumber(1);
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(List.of(existing));

    PublishService.PublishOutcome outcome = service.publish(
      PublishableKind.DATA_OBJECTS,
      "01HF-A",
      "https://shepard.example/v2/data-objects/01HF-A",
      "alice",
      false
    );

    assertFalse(outcome.fresh());
    assertSame(existing, outcome.publication());
    // Critical: no mint, no attach.
    verify(minter, never()).mint(any());
    verify(publicationDAO, never()).attachToEntity(any(), anyString());
    // findLatestVersionNumber is also skipped on the idempotent path.
    verify(publicationDAO, never()).findLatestVersionNumber(anyString());
  }

  @Test
  void forceTrueBumpsVersionEvenWhenPublicationExists() {
    // KIP1h: force-remint now bumps the version instead of stamping
    // a new epoch-millis suffix. The DAO query reports v3 as the
    // current max → next mint is v4.
    primeEntityExists();
    Publication existing = new Publication();
    existing.setPid("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v3");
    existing.setEntityAppId("01HF-A");
    existing.setVersionNumber(3);
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(List.of(existing));
    when(publicationDAO.findLatestVersionNumber("01HF-A")).thenReturn(3);

    ArgumentCaptor<MintRequest> reqCaptor = ArgumentCaptor.forClass(MintRequest.class);
    when(minter.mint(reqCaptor.capture())).thenReturn(
      new MintResult("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v4", Instant.ofEpochMilli(1_747_000_000_001L), "local")
    );
    when(publicationDAO.attachToEntity(any(Publication.class), eq("01HF-A"))).thenAnswer(inv -> inv.getArgument(0));

    PublishService.PublishOutcome outcome = service.publish(
      PublishableKind.DATA_OBJECTS,
      "01HF-A",
      "https://shepard.example/v2/data-objects/01HF-A",
      "alice",
      true
    );

    assertTrue(outcome.fresh());
    assertEquals("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v4", outcome.publication().getPid());
    assertEquals(Integer.valueOf(4), outcome.publication().getVersionNumber());
    // The MintRequest carried versionNumber=4 — plugin authors rely on this.
    assertEquals(4, reqCaptor.getValue().versionNumber());
    verify(minter).mint(any(MintRequest.class));
  }

  @Test
  void firstPublishStampsVersionOne() {
    // Fresh entity, never published. findLatestVersionNumber returns 0,
    // next mint is v1.
    primeEntityExists();
    when(publicationDAO.findByEntityAppId("01HF-FRESH")).thenReturn(List.of());
    when(publicationDAO.findLatestVersionNumber("01HF-FRESH")).thenReturn(0);

    ArgumentCaptor<MintRequest> reqCaptor = ArgumentCaptor.forClass(MintRequest.class);
    when(minter.mint(reqCaptor.capture())).thenReturn(
      new MintResult("shepard:lab-a:data-objects:01HF-FRESH:v1", Instant.ofEpochMilli(1L), "local")
    );
    when(publicationDAO.attachToEntity(any(Publication.class), eq("01HF-FRESH"))).thenAnswer(inv -> inv.getArgument(0));

    PublishService.PublishOutcome outcome = service.publish(
      PublishableKind.DATA_OBJECTS,
      "01HF-FRESH",
      "https://x/v2/data-objects/01HF-FRESH",
      "alice",
      false
    );

    assertEquals(1, reqCaptor.getValue().versionNumber());
    assertEquals(Integer.valueOf(1), outcome.publication().getVersionNumber());
  }

  @Test
  void noActiveMinterRaisesMinterNotInstalled() {
    // KIP1h: when the registry has no active minter the service
    // refuses the call cleanly. The REST layer translates this into
    // 503 RFC 7807 `publish.minter.not-installed`.
    primeEntityExists();
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(List.of());
    when(minterRegistry.activeMinter()).thenReturn(Optional.empty());

    MinterNotInstalledException ex = assertThrows(
      MinterNotInstalledException.class,
      () ->
        service.publish(
          PublishableKind.DATA_OBJECTS,
          "01HF-A",
          "https://shepard.example/v2/data-objects/01HF-A",
          "alice",
          false
        )
    );
    assertTrue(ex.getMessage().contains("plugins/minter-local/"));
    // Critical: when no minter is wired the publish flow must not
    // touch the DAO (no half-written :Publication rows).
    verify(publicationDAO, never()).attachToEntity(any(), anyString());
  }

  @Test
  void unknownEntityThrowsNotFound() {
    Result kindResult = mock(Result.class);
    when(kindResult.iterator()).thenReturn(java.util.Collections.<Map<String, Object>>emptyList().iterator());
    when(session.query(anyString(), any(Map.class))).thenReturn(kindResult);
    NotFoundException ex = assertThrows(
      NotFoundException.class,
      () ->
        service.publish(
          PublishableKind.DATA_OBJECTS,
          "01HF-MISSING",
          "https://shepard.example/v2/data-objects/01HF-MISSING",
          "alice",
          false
        )
    );
    assertTrue(ex.getMessage().contains("DataObject"));
    assertTrue(ex.getMessage().contains("01HF-MISSING"));
  }

  @Test
  void nullKindRejectedEarly() {
    assertThrows(
      IllegalArgumentException.class,
      () -> service.publish(null, "01HF-A", "https://x", "alice", false)
    );
  }

  @Test
  void blankEntityAppIdRejected() {
    assertThrows(
      IllegalArgumentException.class,
      () -> service.publish(PublishableKind.DATA_OBJECTS, "", "https://x", "alice", false)
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> service.publish(PublishableKind.DATA_OBJECTS, null, "https://x", "alice", false)
    );
  }

  @Test
  void currentForReturnsTopOfList() {
    Publication a = new Publication();
    a.setPid("pid-a");
    Publication b = new Publication();
    b.setPid("pid-b");
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(List.of(a, b));
    var out = service.currentFor("01HF-A");
    assertTrue(out.isPresent());
    assertSame(a, out.get());
  }

  @Test
  void currentForReturnsEmptyWhenNoPublication() {
    when(publicationDAO.findByEntityAppId("01HF-X")).thenReturn(List.of());
    assertFalse(service.currentFor("01HF-X").isPresent());
  }
}
