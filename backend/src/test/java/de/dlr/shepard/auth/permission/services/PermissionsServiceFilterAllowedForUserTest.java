package de.dlr.shepard.auth.permission.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.AccessType;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CaffeineCache;
import io.quarkus.cache.CompositeCacheKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PermissionsServiceFilterAllowedForUserTest extends BaseTestCase {

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

  @InjectMocks
  private PermissionsService permissionsService;

  @BeforeEach
  public void wireCache() {
    when(cache.as(CaffeineCache.class)).thenReturn(caffeineCache);
  }

  @Test
  public void emptyInput_returnsEmpty_andDoesNotHitDao() {
    Set<Long> result = permissionsService.filterAllowedForUser(Collections.emptyList(), AccessType.Read, "alice");
    assertThat(result).isEmpty();
    verify(permissionsDAO, never()).findByEntityNeo4jIds(anyCollection());
  }

  @Test
  public void nullInput_returnsEmpty_andDoesNotHitDao() {
    Set<Long> result = permissionsService.filterAllowedForUser(null, AccessType.Read, "alice");
    assertThat(result).isEmpty();
    verify(permissionsDAO, never()).findByEntityNeo4jIds(anyCollection());
  }

  @Test
  public void allCachedAllowed_skipsCypher_returnsAll() {
    primeCache(1L, AccessType.Read, "alice", true);
    primeCache(2L, AccessType.Read, "alice", true);
    primeCache(3L, AccessType.Read, "alice", true);

    Set<Long> result = permissionsService.filterAllowedForUser(List.of(1L, 2L, 3L), AccessType.Read, "alice");

    assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L);
    verify(permissionsDAO, never()).findByEntityNeo4jIds(anyCollection());
  }

  @Test
  public void allCachedDenied_skipsCypher_returnsEmpty() {
    primeCache(1L, AccessType.Write, "bob", false);
    primeCache(2L, AccessType.Write, "bob", false);

    Set<Long> result = permissionsService.filterAllowedForUser(List.of(1L, 2L), AccessType.Write, "bob");

    assertThat(result).isEmpty();
    verify(permissionsDAO, never()).findByEntityNeo4jIds(anyCollection());
  }

  @Test
  public void mixedCacheAndUncached_callsCypherOnceForUncachedSubset_andMergesResults() {
    primeCache(10L, AccessType.Read, "alice", true);
    primeCache(20L, AccessType.Read, "alice", false);
    primeCacheMiss(30L, AccessType.Read, "alice");
    primeCacheMiss(40L, AccessType.Read, "alice");

    var pubPerms = new Permissions() {
      {
        setPermissionType(de.dlr.shepard.common.util.PermissionType.Public);
      }
    };
    var privatePerms = new Permissions() {
      {
        setPermissionType(de.dlr.shepard.common.util.PermissionType.Private);
        setOwner(new User("someone-else"));
      }
    };
    when(permissionsDAO.findByEntityNeo4jIds(any())).thenReturn(Map.of(30L, pubPerms, 40L, privatePerms));

    Set<Long> result = permissionsService.filterAllowedForUser(
      List.of(10L, 20L, 30L, 40L),
      AccessType.Read,
      "alice"
    );

    assertThat(result).containsExactlyInAnyOrder(10L, 30L);
    verify(permissionsDAO, times(1)).findByEntityNeo4jIds(anyCollection());
    verify(caffeineCache).put(eqKey(30L, AccessType.Read, "alice"), any());
    verify(caffeineCache).put(eqKey(40L, AccessType.Read, "alice"), any());
  }

  @Test
  public void allUncached_legacyEntitiesWithoutPermissions_areTreatedAsDenied() {
    // C3 fix (aidocs/51 §8 / aidocs/07 C3): legacy entities lacking
    // a Permissions node now fail-closed (deny). Was: full access for
    // every authenticated user via the old `Roles(false, true, true,
    // true)` fallback.
    primeCacheMiss(50L, AccessType.Read, "alice");
    primeCacheMiss(51L, AccessType.Read, "alice");
    when(permissionsDAO.findByEntityNeo4jIds(any())).thenReturn(Collections.emptyMap());

    Set<Long> result = permissionsService.filterAllowedForUser(List.of(50L, 51L), AccessType.Read, "alice");

    assertThat(result).isEmpty();
    verify(permissionsDAO, times(1)).findByEntityNeo4jIds(anyCollection());
  }

  private void primeCache(long entityId, AccessType accessType, String username, boolean allowed) {
    when(caffeineCache.<Boolean>getIfPresent(eqKey(entityId, accessType, username))).thenReturn(
      CompletableFuture.completedFuture(allowed)
    );
  }

  private void primeCacheMiss(long entityId, AccessType accessType, String username) {
    when(caffeineCache.<Boolean>getIfPresent(eqKey(entityId, accessType, username))).thenReturn(null);
  }

  private static CompositeCacheKey eqKey(long entityId, AccessType accessType, String username) {
    return argThat(arg -> {
      if (!(arg instanceof CompositeCacheKey ck)) return false;
      Object[] elements = ck.getKeyElements();
      return (
        elements.length == 3 &&
        Long.valueOf(entityId).equals(elements[0]) &&
        accessType.equals(elements[1]) &&
        username.equals(elements[2])
      );
    });
  }
}
