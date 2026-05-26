package de.dlr.shepard.auth.permission.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * F6 — unit tests for {@link GraphPolicyDecisionPoint}.
 *
 * <p>Verifies that the PDP seam delegates correctly to
 * {@link PermissionsService} and behaves fail-closed on error.
 */
public class GraphPolicyDecisionPointTest extends BaseTestCase {

  private static final String USERNAME = "alice";
  private static final String APP_ID = "018f4e2c-0001-7000-8000-000000000001";
  private static final long ENTITY_ID = 42L;

  @Mock
  private PermissionsService permissionsService;

  @Mock
  private EntityIdResolver entityIdResolver;

  @InjectMocks
  private GraphPolicyDecisionPoint pdp;

  /**
   * Happy path — allowed: resolver finds the entity; service returns true.
   * Verifies that {@code isAllowed} delegates to
   * {@link PermissionsService#isAccessTypeAllowedForUser(long, AccessType, String)}.
   */
  @Test
  public void isAllowed_delegatesToPermissionsService_allowedCase() {
    when(entityIdResolver.resolveLong(APP_ID)).thenReturn(ENTITY_ID);
    when(permissionsService.isAccessTypeAllowedForUser(ENTITY_ID, AccessType.Read, USERNAME))
        .thenReturn(true);

    boolean result = pdp.isAllowed(USERNAME, "Read", APP_ID);

    assertTrue(result);
    verify(entityIdResolver).resolveLong(APP_ID);
    verify(permissionsService).isAccessTypeAllowedForUser(ENTITY_ID, AccessType.Read, USERNAME);
  }

  /**
   * Happy path — denied: resolver finds the entity; service returns false.
   */
  @Test
  public void isAllowed_delegatesToPermissionsService_deniedCase() {
    when(entityIdResolver.resolveLong(APP_ID)).thenReturn(ENTITY_ID);
    when(permissionsService.isAccessTypeAllowedForUser(ENTITY_ID, AccessType.Write, USERNAME))
        .thenReturn(false);

    boolean result = pdp.isAllowed(USERNAME, "Write", APP_ID);

    assertFalse(result);
    verify(permissionsService).isAccessTypeAllowedForUser(ENTITY_ID, AccessType.Write, USERNAME);
  }

  /**
   * DataObject fallback — resolver throws NotFoundException (DataObject has no own
   * Permissions node); PDP falls back to the DataObject-appId walk and returns
   * whatever the service says.
   */
  @Test
  public void isAllowed_dataObjectFallback_whenEntityIdNotFound() {
    when(entityIdResolver.resolveLong(APP_ID)).thenThrow(new NotFoundException("no entity"));
    when(permissionsService.isAccessAllowedForDataObjectAppId(APP_ID, AccessType.Read, USERNAME))
        .thenReturn(true);

    boolean result = pdp.isAllowed(USERNAME, "Read", APP_ID);

    assertTrue(result);
    verify(permissionsService).isAccessAllowedForDataObjectAppId(APP_ID, AccessType.Read, USERNAME);
  }
}
