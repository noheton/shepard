package de.dlr.shepard.auth.permission.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.healthz.DbHealthRegistry;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.PermissionType;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CaffeineCache;
import io.quarkus.cache.CompositeCacheKey;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PermissionsServiceFilterAllowedUsersTest extends BaseTestCase {

  @Mock
  private PermissionsDAO permissionsDAO;

  @Mock
  private UserService userService;

  @Mock
  private UserGroupService userGroupService;

  @Mock
  private Cache cache;

  @Mock
  private CaffeineCache caffeineCache;

  @Mock
  private DbHealthRegistry dbHealthRegistry;

  @Mock
  private AuthenticationContext authenticationContext;

  @InjectMocks
  private PermissionsService permissionsService;

  @BeforeEach
  public void wireCache() {
    when(cache.as(CaffeineCache.class)).thenReturn(caffeineCache);
    // F4: default to no principal → iat = 0L
    when(authenticationContext.getPrincipal()).thenReturn(null);
  }

  @Test
  public void emptyInput_returnsEmpty_andDoesNotHitDao() {
    Set<String> result = permissionsService.filterAllowedUsers(7L, AccessType.Read, Collections.emptyList());
    assertThat(result).isEmpty();
    verify(permissionsDAO, never()).findByEntityNeo4jId(anyLong());
  }

  @Test
  public void nullInput_returnsEmpty_andDoesNotHitDao() {
    Set<String> result = permissionsService.filterAllowedUsers(7L, AccessType.Read, null);
    assertThat(result).isEmpty();
    verify(permissionsDAO, never()).findByEntityNeo4jId(anyLong());
  }

  @Test
  public void allCachedAllowed_skipsCypher_returnsAll() {
    primeCache(42L, AccessType.Read, "alice", true);
    primeCache(42L, AccessType.Read, "bob", true);
    primeCache(42L, AccessType.Read, "carol", true);

    Set<String> result = permissionsService.filterAllowedUsers(42L, AccessType.Read, List.of("alice", "bob", "carol"));

    assertThat(result).containsExactlyInAnyOrder("alice", "bob", "carol");
    verify(permissionsDAO, never()).findByEntityNeo4jId(anyLong());
  }

  @Test
  public void allCachedDenied_skipsCypher_returnsEmpty() {
    primeCache(42L, AccessType.Write, "alice", false);
    primeCache(42L, AccessType.Write, "bob", false);

    Set<String> result = permissionsService.filterAllowedUsers(42L, AccessType.Write, List.of("alice", "bob"));

    assertThat(result).isEmpty();
    verify(permissionsDAO, never()).findByEntityNeo4jId(anyLong());
  }

  @Test
  public void mixedCacheAndUncached_callsCypherOnceForUncachedSubset_andMergesResults() {
    primeCache(42L, AccessType.Read, "alice", true);
    primeCache(42L, AccessType.Read, "mallory", false);
    primeCacheMiss(42L, AccessType.Read, "owner");
    primeCacheMiss(42L, AccessType.Read, "stranger");

    var perms = new Permissions();
    perms.setPermissionType(PermissionType.Private);
    perms.setOwner(new User("owner"));
    when(permissionsDAO.findByEntityNeo4jId(42L)).thenReturn(perms);

    Set<String> result = permissionsService.filterAllowedUsers(
      42L,
      AccessType.Read,
      List.of("alice", "mallory", "owner", "stranger")
    );

    assertThat(result).containsExactlyInAnyOrder("alice", "owner");
    verify(permissionsDAO, times(1)).findByEntityNeo4jId(42L);
    verify(caffeineCache).put(eqKey(42L, AccessType.Read, "owner", 0L), any());
    verify(caffeineCache).put(eqKey(42L, AccessType.Read, "stranger", 0L), any());
  }

  private void primeCache(long entityId, AccessType accessType, String username, boolean allowed) {
    // F4: iat is 0L when no principal is set (default in wireCache)
    when(caffeineCache.<Boolean>getIfPresent(eqKey(entityId, accessType, username, 0L))).thenReturn(
      CompletableFuture.completedFuture(allowed)
    );
  }

  private void primeCacheMiss(long entityId, AccessType accessType, String username) {
    when(caffeineCache.<Boolean>getIfPresent(eqKey(entityId, accessType, username, 0L))).thenReturn(null);
  }

  private static CompositeCacheKey eqKey(long entityId, AccessType accessType, String username, long iat) {
    return argThat(arg -> {
      if (!(arg instanceof CompositeCacheKey ck)) return false;
      Object[] elements = ck.getKeyElements();
      return (
        elements.length == 4 &&
        Long.valueOf(entityId).equals(elements[0]) &&
        accessType.equals(elements[1]) &&
        username.equals(elements[2]) &&
        Long.valueOf(iat).equals(elements[3])
      );
    });
  }
}
