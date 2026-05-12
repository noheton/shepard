package de.dlr.shepard.auth.permission.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

class OrphanPermissionsBackfillContextTest {

  Driver driver;
  Session session;

  @BeforeEach
  void setUp() {
    driver = mock(Driver.class);
    session = mock(Session.class);
    when(driver.session()).thenReturn(session);
  }

  /**
   * Happy path: orphans exist + config set + user exists → seeds the
   * context node so V14 picks up the configured owner.
   */
  @Test
  void prepare_orphansExistAndOwnerConfigured_seedsContext() {
    Result orphanCountResult = mock(Result.class);
    Record orphanRow = mock(Record.class);
    Value orphanCount = mock(Value.class);
    when(orphanCount.asLong()).thenReturn(3L);
    when(orphanRow.get("c")).thenReturn(orphanCount);
    when(orphanCountResult.single()).thenReturn(orphanRow);

    Result userExistsResult = mock(Result.class);
    Record userRow = mock(Record.class);
    Value userExists = mock(Value.class);
    when(userExists.asBoolean()).thenReturn(true);
    when(userRow.get("exists")).thenReturn(userExists);
    when(userExistsResult.single()).thenReturn(userRow);

    Result seedResult = mock(Result.class);
    when(seedResult.consume()).thenReturn(null);

    when(session.run(anyString())).thenReturn(orphanCountResult);
    when(session.run(anyString(), anyMap())).thenAnswer(inv -> {
      String cypher = inv.getArgument(0, String.class);
      if (cypher.contains("count(u)")) return userExistsResult;
      return seedResult;
    });

    var ctx = new OrphanPermissionsBackfillContext(driver, "alice");
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(ctx::prepare);

    verify(session, atLeastOnce()).run(anyString(), anyMap());
  }

  /**
   * Refusal path: orphans exist + default-owner unset → throws.
   */
  @Test
  void prepare_orphansExistAndOwnerUnset_aborts() {
    Result orphanCountResult = mock(Result.class);
    Record orphanRow = mock(Record.class);
    Value orphanCount = mock(Value.class);
    when(orphanCount.asLong()).thenReturn(5L);
    when(orphanRow.get("c")).thenReturn(orphanCount);
    when(orphanCountResult.single()).thenReturn(orphanRow);
    when(session.run(anyString())).thenReturn(orphanCountResult);

    var ctx = new OrphanPermissionsBackfillContext(driver, null);
    var ex = assertThrows(
      OrphanPermissionsBackfillContext.OrphanPermissionsConfigException.class,
      ctx::prepare
    );
    org.junit.jupiter.api.Assertions.assertTrue(
      ex.getMessage().contains("default-owner"),
      "expected 'default-owner' in message, got: " + ex.getMessage()
    );
  }

  /**
   * No orphans + no config → happy path (greenfield install).
   */
  @Test
  void prepare_noOrphansNoOwner_succeedsAndCleansContext() {
    Result orphanCountResult = mock(Result.class);
    Record row = mock(Record.class);
    Value v = mock(Value.class);
    when(v.asLong()).thenReturn(0L);
    when(row.get("c")).thenReturn(v);
    when(orphanCountResult.single()).thenReturn(row);
    when(session.run(anyString())).thenReturn(orphanCountResult);

    Result wipeResult = mock(Result.class);
    when(wipeResult.consume()).thenReturn(null);
    when(session.run("MATCH (ctx:_ShepardMigrationContext) DETACH DELETE ctx")).thenReturn(wipeResult);

    var ctx = new OrphanPermissionsBackfillContext(driver, null);
    ctx.prepare();
    verify(session, times(1)).run("MATCH (ctx:_ShepardMigrationContext) DETACH DELETE ctx");
  }

  @Test
  void prepare_ownerConfiguredButUserMissing_aborts() {
    Result orphanCountResult = mock(Result.class);
    Record orphanRow = mock(Record.class);
    Value orphanCount = mock(Value.class);
    when(orphanCount.asLong()).thenReturn(0L);
    when(orphanRow.get("c")).thenReturn(orphanCount);
    when(orphanCountResult.single()).thenReturn(orphanRow);

    Result userExistsResult = mock(Result.class);
    Record userRow = mock(Record.class);
    Value userExists = mock(Value.class);
    when(userExists.asBoolean()).thenReturn(false);
    when(userRow.get("exists")).thenReturn(userExists);
    when(userExistsResult.single()).thenReturn(userRow);

    when(session.run(anyString())).thenReturn(orphanCountResult);
    when(session.run(anyString(), anyMap())).thenReturn(userExistsResult);

    var ctx = new OrphanPermissionsBackfillContext(driver, "ghost");
    var ex = assertThrows(
      OrphanPermissionsBackfillContext.OrphanPermissionsConfigException.class,
      ctx::prepare
    );
    assertEquals(true, ex.getMessage().contains("ghost"));
  }
}
