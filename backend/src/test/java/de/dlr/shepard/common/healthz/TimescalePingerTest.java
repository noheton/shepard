package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

public class TimescalePingerTest {

  @Test
  public void successfulQuery_recordsSuccess() {
    EntityManager em = mock(EntityManager.class);
    Query q = mock(Query.class);
    when(em.createNativeQuery("SELECT 1")).thenReturn(q);
    when(q.getSingleResult()).thenReturn(1);

    TimescalePinger p = new TimescalePinger();
    p.entityManager = em;

    assertTrue(p.ping());
    assertTrue(p.state().hasEverBeenUp());
  }

  @Test
  public void queryFailure_recordsFailure() {
    EntityManager em = mock(EntityManager.class);
    Query q = mock(Query.class);
    when(em.createNativeQuery("SELECT 1")).thenReturn(q);
    when(q.getSingleResult()).thenThrow(new PersistenceException("dead"));

    TimescalePinger p = new TimescalePinger();
    p.entityManager = em;

    assertFalse(p.ping());
    assertFalse(p.state().hasEverBeenUp());
  }
}
