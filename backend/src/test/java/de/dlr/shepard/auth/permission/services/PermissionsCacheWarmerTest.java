package de.dlr.shepard.auth.permission.services;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.MostUsedEntityProvider.EntityAccessTriple;
import de.dlr.shepard.common.util.AccessType;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class PermissionsCacheWarmerTest {

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  MostUsedEntityProvider provider;

  @Inject
  PermissionsCacheWarmer warmer;

  @Test
  @TestConfigProperty(key = "shepard.permissions.cache.warm.enabled", value = "true")
  @TestConfigProperty(key = "shepard.permissions.cache.warm.max-entries", value = "3")
  public void enabled_populatesCacheViaPermissionsService() throws Exception {
    when(provider.findMostUsedEntities(3)).thenReturn(
      List.of(
        new EntityAccessTriple(1L, AccessType.Read, "alice"),
        new EntityAccessTriple(2L, AccessType.Read, "bob"),
        new EntityAccessTriple(3L, AccessType.Write, "alice")
      )
    );

    warmer.onStart(new StartupEvent());
    warmer.warmingFuture().get(5, TimeUnit.SECONDS);

    verify(provider).findMostUsedEntities(3);
    verify(permissionsService).isAccessTypeAllowedForUser(1L, AccessType.Read, "alice", 0L);
    verify(permissionsService).isAccessTypeAllowedForUser(2L, AccessType.Read, "bob", 0L);
    verify(permissionsService).isAccessTypeAllowedForUser(3L, AccessType.Write, "alice", 0L);
  }

  @Test
  @TestConfigProperty(key = "shepard.permissions.cache.warm.enabled", value = "false")
  public void disabled_doesNothing() throws Exception {
    warmer.onStart(new StartupEvent());
    warmer.warmingFuture().get(1, TimeUnit.SECONDS);

    verify(provider, never()).findMostUsedEntities(anyInt());
    verify(permissionsService, never()).isAccessTypeAllowedForUser(eq(anyLong()), eq(eq(AccessType.Read)), eq(eq("alice")), anyLong());
  }

  @Test
  @TestConfigProperty(key = "shepard.permissions.cache.warm.enabled", value = "true")
  @TestConfigProperty(key = "shepard.permissions.cache.warm.max-entries", value = "10")
  public void providerException_isSwallowed() throws Exception {
    when(provider.findMostUsedEntities(10)).thenThrow(new RuntimeException("neo4j down"));

    warmer.onStart(new StartupEvent());
    warmer.warmingFuture().get(5, TimeUnit.SECONDS);

    verify(provider, times(1)).findMostUsedEntities(10);
    verify(permissionsService, never()).isAccessTypeAllowedForUser(eq(anyLong()), eq(eq(AccessType.Read)), eq(eq("alice")), anyLong());
  }
}
