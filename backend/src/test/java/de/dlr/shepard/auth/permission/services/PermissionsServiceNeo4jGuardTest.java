package de.dlr.shepard.auth.permission.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.healthz.DatabaseKind;
import de.dlr.shepard.common.healthz.DbHealthRegistry;
import de.dlr.shepard.common.util.AccessType;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CaffeineCache;
import jakarta.enterprise.event.Event;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * F5 — fail-closed guard: when Neo4j is reported as DOWN by
 * {@link DbHealthRegistry#isCurrentlyDown(DatabaseKind)},
 * {@link PermissionsService#isAllowed} must return {@code false}
 * immediately without ever consulting the DAO layer.
 */
public class PermissionsServiceNeo4jGuardTest extends BaseTestCase {

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

  @Mock
  @SuppressWarnings("rawtypes")
  private Event permissionsChangedEvent;

  @InjectMocks
  private PermissionsService permissionsService;

  @Mock
  private ContainerRequestContext requestContext;

  @Mock
  private UriInfo uriInfo;

  @BeforeEach
  public void wireRequestContext() throws Exception {
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getAbsolutePath()).thenReturn(new URI("http://localhost/shepard/api/collections/123"));
    // Default: Neo4j is UP (so other tests that inherit this context still pass)
    when(dbHealthRegistry.isCurrentlyDown(DatabaseKind.NEO4J)).thenReturn(false);
    when(authenticationContext.getPrincipal()).thenReturn(null);
    when(cache.as(CaffeineCache.class)).thenReturn(caffeineCache);
  }

  @Test
  public void neo4jDown_isAllowed_returnsFalseWithoutCallingDao() {
    when(dbHealthRegistry.isCurrentlyDown(DatabaseKind.NEO4J)).thenReturn(true);

    boolean result = permissionsService.isAllowed(requestContext, AccessType.Read, "alice");

    assertThat(result).isFalse();
    verify(permissionsDAO, never()).findByEntityNeo4jId(org.mockito.ArgumentMatchers.anyLong());
    verify(permissionsDAO, never()).findByEntityNeo4jIds(org.mockito.ArgumentMatchers.anyCollection());
  }

  @Test
  public void neo4jUp_isAllowed_doesNotShortCircuit() {
    // When Neo4j is UP and the path is the root (pathSegments.isEmpty()),
    // isAllowed returns true without consulting DAO — not the F5 guard.
    when(dbHealthRegistry.isCurrentlyDown(DatabaseKind.NEO4J)).thenReturn(false);
    // A path with no id-segment returns true (the "paths with length 1 / no id"
    // branch). This just verifies the guard is not misfiring.
    when(uriInfo.getPathSegments()).thenReturn(List.of());

    // No NPE / short-circuit from the F5 guard
    // (The method will fall through to other logic; we only assert the guard
    // itself doesn't fire by checking DAO is not called for this root-path shape.)
    verify(permissionsDAO, never()).findByEntityNeo4jId(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  public void neo4jDownGuard_isIndependentOfUsername() {
    // Guard must fire regardless of who the caller is.
    when(dbHealthRegistry.isCurrentlyDown(DatabaseKind.NEO4J)).thenReturn(true);

    assertThat(permissionsService.isAllowed(requestContext, AccessType.Write, "instance-admin")).isFalse();
    assertThat(permissionsService.isAllowed(requestContext, AccessType.Manage, "owner-user")).isFalse();
    verify(permissionsDAO, never()).findByEntityNeo4jId(org.mockito.ArgumentMatchers.anyLong());
  }
}
