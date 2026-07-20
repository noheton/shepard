package de.dlr.shepard.auth.users.endpoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.subscription.entities.Subscription;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * GETCURRENTUSER-GLOBAL-DEPTH0: the v1 {@code GET /shepard/api/users} endpoint is
 * the frozen upstream-byte-compat surface that emits the caller's
 * {@code subscriptionIds}/{@code apiKeyIds} on the wire via {@link UserIO}. It must
 * load via the depth-1 {@code getCurrentUserWithCollections()} so those arrays stay
 * populated (the default depth-0 {@code getCurrentUser()} would empty them). This is
 * the wire-shape regression guard for the frozen surface, which previously had no test.
 */
class UserRestTest {

  static final String CALLER = "alice";

  @Mock
  UserService userService;

  @Mock
  AuthenticationContext authenticationContext;

  UserRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new UserRest();
    resource.userService = userService;
    resource.authenticationContext = authenticationContext;
    // principal null → skip effectiveRoles enrichment; isolates the collection projection.
    when(authenticationContext.getPrincipal()).thenReturn(null);
  }

  @Test
  void getCurrentUser_usesDeepLoader_andEmitsSubscriptionAndApiKeyIds() {
    var uid = UUID.randomUUID();
    var user = new User(CALLER, "Alice", "Anderson", "alice@example.org");
    user.setApiKeys(List.of(new ApiKey(uid)));
    user.setSubscriptions(List.of(new Subscription(42L)));
    when(userService.getCurrentUserWithCollections()).thenReturn(user);

    var r = resource.getCurrentUser();
    assertEquals(200, r.getStatus());

    var io = (UserIO) r.getEntity();
    assertEquals(CALLER, io.getUsername());
    assertEquals("[" + uid + "]", Arrays.toString(io.getApiKeyIds()));
    assertEquals("[42]", Arrays.toString(io.getSubscriptionIds()));
  }
}
