package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.configuration.feature.toggles.SpatialDataFeatureToggle;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

public class PostGisPingerTest {

  @Test
  @SuppressWarnings("unchecked")
  public void toggleOff_returnsUpWithoutTouchingEntityManager() {
    Instance<EntityManager> instance = mock(Instance.class);
    PostGisPinger p = new PostGisPinger();
    p.spatialEntityManager = instance;

    try (MockedStatic<SpatialDataFeatureToggle> ms = mockStatic(SpatialDataFeatureToggle.class)) {
      ms.when(SpatialDataFeatureToggle::isActive).thenReturn(false);

      assertFalse(p.isRequired());
      assertTrue(p.ping());
      verify(instance, never()).get();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void toggleOn_pingsAndRecordsSuccess() {
    Instance<EntityManager> instance = mock(Instance.class);
    EntityManager em = mock(EntityManager.class);
    Query q = mock(Query.class);
    when(instance.isResolvable()).thenReturn(true);
    when(instance.get()).thenReturn(em);
    when(em.createNativeQuery("SELECT 1")).thenReturn(q);
    when(q.getSingleResult()).thenReturn(1);

    PostGisPinger p = new PostGisPinger();
    p.spatialEntityManager = instance;

    try (MockedStatic<SpatialDataFeatureToggle> ms = mockStatic(SpatialDataFeatureToggle.class)) {
      ms.when(SpatialDataFeatureToggle::isActive).thenReturn(true);

      assertTrue(p.isRequired());
      assertTrue(p.ping());
      assertTrue(p.state().hasEverBeenUp());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void toggleOn_failureRecorded() {
    Instance<EntityManager> instance = mock(Instance.class);
    EntityManager em = mock(EntityManager.class);
    Query q = mock(Query.class);
    when(instance.isResolvable()).thenReturn(true);
    when(instance.get()).thenReturn(em);
    when(em.createNativeQuery("SELECT 1")).thenReturn(q);
    when(q.getSingleResult()).thenThrow(new RuntimeException("offline"));

    PostGisPinger p = new PostGisPinger();
    p.spatialEntityManager = instance;

    try (MockedStatic<SpatialDataFeatureToggle> ms = mockStatic(SpatialDataFeatureToggle.class)) {
      ms.when(SpatialDataFeatureToggle::isActive).thenReturn(true);

      assertFalse(p.ping());
      assertFalse(p.state().hasEverBeenUp());
    }
  }
}
