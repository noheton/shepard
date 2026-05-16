package de.dlr.shepard.auth.permission.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.healthz.DbHealthRegistry;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.PermissionType;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CaffeineCache;
import io.quarkus.cache.CompositeCacheKey;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * F4 — verifies that the 4th cache-key dimension (JWT {@code iat})
 * is wired correctly in {@link PermissionsService}:
 *
 * <ul>
 *   <li>Two requests with different {@code iat} values produce distinct
 *       cache keys (a new token always misses the cache).
 *   <li>A request with no principal (API-key path) uses {@code iat = 0L}.
 *   <li>Cache writes include the {@code iat} dimension.
 * </ul>
 */
public class PermissionsServiceIatCacheKeyTest extends BaseTestCase {

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
  public void setup() {
    when(cache.as(CaffeineCache.class)).thenReturn(caffeineCache);
    // Neo4j is UP for these tests
    when(dbHealthRegistry.isCurrentlyDown(any())).thenReturn(false);
  }

  private JWTPrincipal principalWithIat(long iat) {
    return new JWTPrincipal("aud", "client", "alice", "kid", List.of(), iat);
  }

  // F4: cache key shape for filterAllowedForUser

  @Test
  public void differentIat_missCache_andQueryDaoOnce() {
    long iat1 = 1_700_000_000L;
    long iat2 = 1_700_100_000L;

    // With iat1: alice@entity10 is cached allowed; alice@entity20 is a miss
    primeCache(10L, AccessType.Read, "alice", iat1, true);
    primeCacheMiss(20L, AccessType.Read, "alice", iat1);

    // With iat2: both are cache misses (different iat key)
    primeCacheMiss(10L, AccessType.Read, "alice", iat2);
    primeCacheMiss(20L, AccessType.Read, "alice", iat2);

    // --- Request with iat1 ---
    when(authenticationContext.getPrincipal()).thenReturn(principalWithIat(iat1));

    var pubPerms = new Permissions();
    pubPerms.setPermissionType(PermissionType.Public);
    when(permissionsDAO.findByEntityNeo4jIds(any())).thenReturn(java.util.Map.of(20L, pubPerms));

    Set<Long> result1 = permissionsService.filterAllowedForUser(List.of(10L, 20L), AccessType.Read, "alice");
    assertThat(result1).containsExactlyInAnyOrder(10L, 20L);
    verify(permissionsDAO, times(1)).findByEntityNeo4jIds(any());

    // --- Request with iat2: both ids are uncached; DAO is called again ---
    when(authenticationContext.getPrincipal()).thenReturn(principalWithIat(iat2));
    when(permissionsDAO.findByEntityNeo4jIds(any())).thenReturn(
      java.util.Map.of(10L, pubPerms, 20L, pubPerms)
    );

    Set<Long> result2 = permissionsService.filterAllowedForUser(List.of(10L, 20L), AccessType.Read, "alice");
    assertThat(result2).containsExactlyInAnyOrder(10L, 20L);
    // DAO should have been called a second time for the iat2 request
    verify(permissionsDAO, times(2)).findByEntityNeo4jIds(any());
  }

  @Test
  public void noPrincipal_usesZeroIatKey() {
    // No principal → iat = 0L
    when(authenticationContext.getPrincipal()).thenReturn(null);

    primeCache(42L, AccessType.Read, "alice", 0L, true);
    primeCacheMiss(43L, AccessType.Read, "alice", 0L);

    var pubPerms = new Permissions();
    pubPerms.setPermissionType(PermissionType.Public);
    when(permissionsDAO.findByEntityNeo4jIds(any())).thenReturn(java.util.Map.of(43L, pubPerms));

    Set<Long> result = permissionsService.filterAllowedForUser(List.of(42L, 43L), AccessType.Read, "alice");
    assertThat(result).containsExactlyInAnyOrder(42L, 43L);
    verify(permissionsDAO, times(1)).findByEntityNeo4jIds(any());
  }

  @Test
  public void cacheWrite_includesIatDimension() {
    long iat = 1_700_000_000L;
    when(authenticationContext.getPrincipal()).thenReturn(principalWithIat(iat));

    primeCacheMiss(99L, AccessType.Read, "alice", iat);

    var pubPerms = new Permissions();
    pubPerms.setPermissionType(PermissionType.Public);
    when(permissionsDAO.findByEntityNeo4jIds(any())).thenReturn(java.util.Map.of(99L, pubPerms));

    permissionsService.filterAllowedForUser(List.of(99L), AccessType.Read, "alice");

    // The cache write must include iat as the 4th element
    verify(caffeineCache).put(eqKey(99L, AccessType.Read, "alice", iat), any());
  }

  // filterAllowedUsers symmetric test

  @Test
  public void filterAllowedUsers_cacheWrite_includesIatDimension() {
    long iat = 1_600_000_000L;
    when(authenticationContext.getPrincipal()).thenReturn(principalWithIat(iat));

    primeCacheMissForUsers(42L, AccessType.Read, "owner", iat);

    var perms = new Permissions();
    perms.setPermissionType(PermissionType.Private);
    perms.setOwner(new User("owner"));
    when(permissionsDAO.findByEntityNeo4jId(42L)).thenReturn(perms);

    Set<String> result = permissionsService.filterAllowedUsers(42L, AccessType.Read, List.of("owner"));
    assertThat(result).containsExactlyInAnyOrder("owner");

    verify(caffeineCache).put(eqKey(42L, AccessType.Read, "owner", iat), any());
  }

  // helpers

  private void primeCache(long entityId, AccessType at, String username, long iat, boolean allowed) {
    when(caffeineCache.<Boolean>getIfPresent(eqKey(entityId, at, username, iat))).thenReturn(
      CompletableFuture.completedFuture(allowed)
    );
  }

  private void primeCacheMiss(long entityId, AccessType at, String username, long iat) {
    when(caffeineCache.<Boolean>getIfPresent(eqKey(entityId, at, username, iat))).thenReturn(null);
  }

  private void primeCacheMissForUsers(long entityId, AccessType at, String username, long iat) {
    when(caffeineCache.<Boolean>getIfPresent(eqKey(entityId, at, username, iat))).thenReturn(null);
  }

  private static CompositeCacheKey eqKey(long entityId, AccessType at, String username, long iat) {
    return argThat(arg -> {
      if (!(arg instanceof CompositeCacheKey ck)) return false;
      Object[] elements = ck.getKeyElements();
      return (
        elements.length == 4 &&
        Long.valueOf(entityId).equals(elements[0]) &&
        at.equals(elements[1]) &&
        username.equals(elements[2]) &&
        Long.valueOf(iat).equals(elements[3])
      );
    });
  }
}
