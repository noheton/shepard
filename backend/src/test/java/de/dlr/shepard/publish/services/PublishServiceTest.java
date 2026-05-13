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
import de.dlr.shepard.publish.minter.MinterRegistry;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    when(minterRegistry.activeMinter()).thenReturn(minter);
    when(minter.id()).thenReturn("mock");

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
    when(minter.mint(any(MintRequest.class))).thenReturn(
      new MintResult("mock:shepard:data-objects:01HF-A:1747000000000", Instant.ofEpochMilli(1_747_000_000_000L), "mock")
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
    assertEquals("mock:shepard:data-objects:01HF-A:1747000000000", outcome.publication().getPid());
    assertEquals("alice", outcome.publication().getPublishedBy());
    assertEquals("data-objects", outcome.publication().getEntityKind());
    assertEquals("01HF-A", outcome.publication().getEntityAppId());
    assertEquals("mock", outcome.publication().getMinterId());
    verify(publicationDAO).attachToEntity(any(Publication.class), eq("01HF-A"));
  }

  @Test
  void idempotentPublishReturnsExistingWithoutMinting() {
    primeEntityExists();
    Publication existing = new Publication();
    existing.setAppId("pub-app-id-existing");
    existing.setPid("mock:shepard:data-objects:01HF-A:1700000000000");
    existing.setMintedAt(1_700_000_000_000L);
    existing.setMinterId("mock");
    existing.setEntityAppId("01HF-A");
    existing.setEntityKind("data-objects");
    existing.setPublishedBy("alice");
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
  }

  @Test
  void forceTrueMintsFreshEvenWhenPublicationExists() {
    primeEntityExists();
    Publication existing = new Publication();
    existing.setPid("mock:shepard:data-objects:01HF-A:1700000000000");
    existing.setEntityAppId("01HF-A");
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(List.of(existing));
    when(minter.mint(any(MintRequest.class))).thenReturn(
      new MintResult("mock:shepard:data-objects:01HF-A:1747000000001", Instant.ofEpochMilli(1_747_000_000_001L), "mock")
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
    assertEquals("mock:shepard:data-objects:01HF-A:1747000000001", outcome.publication().getPid());
    verify(minter).mint(any(MintRequest.class));
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
