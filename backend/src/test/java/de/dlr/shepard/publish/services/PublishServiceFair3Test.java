package de.dlr.shepard.publish.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.publish.PublishableKind;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.publish.minter.MintRequest;
import de.dlr.shepard.publish.minter.MintResult;
import de.dlr.shepard.publish.minter.Minter;
import de.dlr.shepard.publish.minter.MinterRegistry;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * Unit tests for FAIR3 — accessRights enforcement in PublishService.publish().
 *
 * <p>Tests the four FAIR3 behavioural requirements specified in the task:
 *
 * <ol>
 *   <li>force=false + RESTRICTED → throws IllegalStateException</li>
 *   <li>force=false + EMBARGOED  → throws IllegalStateException</li>
 *   <li>force=true  + RESTRICTED → succeeds (force overrides)</li>
 *   <li>force=false + OPEN       → succeeds normally</li>
 * </ol>
 */
class PublishServiceFair3Test {

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
    service.entityIdResolver = mock(EntityIdResolver.class);
  }

  /**
   * Prime the session mocks to simulate an entity that exists with a given accessRights value.
   *
   * <p>Query order depends on the force flag and expected path:
   * <ul>
   *   <li>Always: verifyEntityKind (query 1)</li>
   *   <li>When force=false: readAccessRights (query 2). If RESTRICTED/EMBARGOED, throws here.
   *       If OPEN, also: idempotency check hits publicationDAO (not session), then buildMetadata (query 3).</li>
   *   <li>When force=true: readAccessRights is SKIPPED; buildMetadata is query 2.</li>
   * </ul>
   *
   * @param accessRights  the accessRights value to return from Neo4j (or null = absent)
   * @param isForced      whether force=true is being used (affects query sequencing)
   * @param expectsMint   whether the call is expected to reach the mint path
   */
  private void primeEntityWithAccessRights(String accessRights, boolean isForced, boolean expectsMint) {
    // verifyEntityKind result
    Result kindResult = mock(Result.class);
    when(kindResult.iterator()).thenReturn(
      List.<Map<String, Object>>of(Map.of("appId", "01HF-A")).iterator()
    );

    if (isForced) {
      // force=true: no readAccessRights call; goes straight to buildMetadata (if minting)
      if (expectsMint) {
        Result metaResult = mock(Result.class);
        when(metaResult.iterator()).thenReturn(
          List.<Map<String, Object>>of(
            Map.of("name", "TR-004", "createdAt", 1_700_000_000_000L,
                   "updatedAt", 1_700_000_100_000L, "createdByUsername", "alice")
          ).iterator()
        );
        when(session.query(anyString(), any(Map.class)))
          .thenReturn(kindResult)   // verifyEntityKind
          .thenReturn(metaResult);  // buildMetadata
      } else {
        when(session.query(anyString(), any(Map.class)))
          .thenReturn(kindResult);
      }
    } else {
      // force=false: verifyEntityKind → readAccessRights [→ buildMetadata if expectsMint]
      Result arResult = mock(Result.class);
      when(arResult.iterator()).thenReturn(
        accessRights != null
          ? List.<Map<String, Object>>of(Map.of("accessRights", accessRights)).iterator()
          : Collections.<Map<String, Object>>emptyList().iterator()
      );

      if (expectsMint) {
        Result metaResult = mock(Result.class);
        when(metaResult.iterator()).thenReturn(
          List.<Map<String, Object>>of(
            Map.of("name", "TR-004", "createdAt", 1_700_000_000_000L,
                   "updatedAt", 1_700_000_100_000L, "createdByUsername", "alice")
          ).iterator()
        );
        when(session.query(anyString(), any(Map.class)))
          .thenReturn(kindResult)   // verifyEntityKind
          .thenReturn(arResult)     // readAccessRights
          .thenReturn(metaResult);  // buildMetadata
      } else {
        when(session.query(anyString(), any(Map.class)))
          .thenReturn(kindResult)   // verifyEntityKind
          .thenReturn(arResult);    // readAccessRights
      }
    }
  }

  // ── FAIR3 test 5: force=false + RESTRICTED → throws ─────────────────────

  @Test
  void publish_throwsIllegalState_whenRestrictedAndNotForced() {
    primeEntityWithAccessRights("RESTRICTED", false, false);

    IllegalStateException ex = assertThrows(
      IllegalStateException.class,
      () -> service.publish(
        PublishableKind.DATA_OBJECTS,
        "01HF-A",
        "https://shepard.example/v2/data-objects/01HF-A",
        "alice",
        false
      )
    );
    assertTrue(ex.getMessage().contains("RESTRICTED"),
      "Error message must mention the offending accessRights value");
    assertTrue(ex.getMessage().contains("force=true"),
      "Error message must mention the force=true escape hatch");

    // No mint, no publication persisted.
    verify(minter, never()).mint(any());
    verify(publicationDAO, never()).attachToEntity(any(), anyString());
  }

  // ── FAIR3 test 6: force=false + EMBARGOED → throws ──────────────────────

  @Test
  void publish_throwsIllegalState_whenEmbargoedAndNotForced() {
    primeEntityWithAccessRights("EMBARGOED", false, false);

    IllegalStateException ex = assertThrows(
      IllegalStateException.class,
      () -> service.publish(
        PublishableKind.DATA_OBJECTS,
        "01HF-A",
        "https://shepard.example/v2/data-objects/01HF-A",
        "alice",
        false
      )
    );
    assertTrue(ex.getMessage().contains("EMBARGOED"),
      "Error message must mention the offending accessRights value");

    verify(minter, never()).mint(any());
    verify(publicationDAO, never()).attachToEntity(any(), anyString());
  }

  // ── FAIR3 test 7: force=true + RESTRICTED → succeeds ────────────────────

  @Test
  void publish_succeeds_whenRestrictedAndForced() {
    primeEntityWithAccessRights("RESTRICTED", true, true);
    when(publicationDAO.findLatestVersionNumber("01HF-A")).thenReturn(0);
    when(minter.mint(any(MintRequest.class))).thenReturn(
      new MintResult("shepard:lab:data-objects:01HF-A:v1", Instant.ofEpochMilli(1_747_000_000_000L), "local")
    );
    when(publicationDAO.attachToEntity(any(Publication.class), eq("01HF-A")))
      .thenAnswer(inv -> inv.getArgument(0));

    // force=true bypasses the FAIR3 gate and proceeds to mint.
    PublishService.PublishOutcome outcome = assertDoesNotThrow(
      () -> service.publish(
        PublishableKind.DATA_OBJECTS,
        "01HF-A",
        "https://shepard.example/v2/data-objects/01HF-A",
        "alice",
        true
      )
    );
    assertTrue(outcome.fresh(), "force=true must produce a fresh mint");
    verify(minter).mint(any(MintRequest.class));
  }

  // ── FAIR3 test 8: force=false + OPEN → succeeds normally ────────────────

  @Test
  void publish_succeeds_whenOpenAndNotForced() {
    primeEntityWithAccessRights("OPEN", false, true);
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(List.of());
    when(publicationDAO.findLatestVersionNumber("01HF-A")).thenReturn(0);
    when(minter.mint(any(MintRequest.class))).thenReturn(
      new MintResult("shepard:lab:data-objects:01HF-A:v1", Instant.ofEpochMilli(1_747_000_000_000L), "local")
    );
    when(publicationDAO.attachToEntity(any(Publication.class), eq("01HF-A")))
      .thenAnswer(inv -> inv.getArgument(0));

    PublishService.PublishOutcome outcome = assertDoesNotThrow(
      () -> service.publish(
        PublishableKind.DATA_OBJECTS,
        "01HF-A",
        "https://shepard.example/v2/data-objects/01HF-A",
        "alice",
        false
      )
    );
    assertTrue(outcome.fresh(), "OPEN entity must be published successfully");
    verify(minter).mint(any(MintRequest.class));
  }
}
