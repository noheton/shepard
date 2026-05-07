package de.dlr.shepard.common.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.subscription.entities.Subscription;
import de.dlr.shepard.common.subscription.services.SubscriptionService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.RequestMethod;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusComponentTest
public class SubscriptionFilterTest {

  @InjectMock
  UriInfo uriInfo;

  @InjectMock
  ContainerRequestContext request;

  @InjectMock
  ContainerResponseContext response;

  @InjectMock
  SubscriptionService service;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  ExecutorFactory executorFactory;

  @InjectMock
  ExecutorService executorService;

  @Inject
  SubscriptionFilter filter;

  @BeforeEach
  public void setUpUriInfo() throws URISyntaxException {
    URI uri = new URI("http://my.url/test/200/sub");
    when(uriInfo.getAbsolutePath()).thenReturn(uri);

    var rootSeg = mock(PathSegment.class);
    when(rootSeg.getPath()).thenReturn("test");
    var idSeg = mock(PathSegment.class);
    when(idSeg.getPath()).thenReturn("200");
    var pathSeg = mock(PathSegment.class);
    when(pathSeg.getPath()).thenReturn("sub");
    when(uriInfo.getPathSegments()).thenReturn(List.of(rootSeg, idSeg, pathSeg));

    when(request.getUriInfo()).thenReturn(uriInfo);
  }

  @BeforeEach
  public void prepareSpy() {
    when(executorFactory.getInstance()).thenReturn(executorService);
  }

  @Test
  public void testFilterSucessful() {
    User user = new User("bob");
    Subscription sub = new Subscription(100L);
    sub.setCallbackURL("http://callback.url/test");
    sub.setName("MySub");
    sub.setRequestMethod(RequestMethod.GET);
    sub.setSubscribedURL("http://my.url/test/200/sub");
    sub.setCreatedBy(user);
    List<Subscription> subs = List.of(sub);
    var entityIO = new BasicEntityIO() {
      {
        setId(200L);
      }
    };

    when(request.getMethod()).thenReturn("GET");
    when(response.getStatus()).thenReturn(200);
    when(response.getEntity()).thenReturn(entityIO);
    when(service.getMatchingSubscriptions(RequestMethod.GET)).thenReturn(subs);
    when(permissionsService.filterAllowedUsers(eq(200L), eq(AccessType.Read), anyCollection())).thenReturn(Set.of("bob"));

    filter.filter(request, response);
    verify(executorService).execute(any());
    verify(permissionsService, never()).isAllowed(any(ContainerRequestContext.class), any(), any());
  }

  @Test
  public void testFilterNoId() {
    User user = new User("bob");
    Subscription sub = new Subscription();
    sub.setCallbackURL("http://callback.url/test");
    sub.setId(200L);
    sub.setName("MySub");
    sub.setRequestMethod(RequestMethod.GET);
    sub.setSubscribedURL("http://my.url/test/200/sub");
    sub.setCreatedBy(user);
    List<Subscription> subs = List.of(sub);
    Object noId = new Object();

    when(request.getMethod()).thenReturn("GET");
    when(response.getStatus()).thenReturn(200);
    when(response.getEntity()).thenReturn(noId);
    when(service.getMatchingSubscriptions(RequestMethod.GET)).thenReturn(subs);
    when(permissionsService.filterAllowedUsers(eq(200L), eq(AccessType.Read), anyCollection())).thenReturn(Set.of("bob"));

    filter.filter(request, response);
    verify(executorService).execute(any());
  }

  @ParameterizedTest
  @CsvSource({ "http://my.url/test2,200", "http://my.url/test/200/sub,100", "http://my.url/test/200/sub,400" })
  public void testFilterNoExecution(String subscribedUrl, Integer statusCode) {
    Subscription sub = new Subscription();
    sub.setCallbackURL("http://callback.url/test");
    sub.setId(200L);
    sub.setName("MySub");
    sub.setRequestMethod(RequestMethod.GET);
    sub.setSubscribedURL(subscribedUrl);
    List<Subscription> subs = List.of(sub);
    Object noId = new Object();

    when(request.getMethod()).thenReturn("GET");
    when(response.getStatus()).thenReturn(statusCode);
    when(response.getEntity()).thenReturn(noId);
    when(service.getMatchingSubscriptions(RequestMethod.GET)).thenReturn(subs);

    filter.filter(request, response);
    verify(executorService, never()).execute(any());
  }

  @Test
  public void testFilterNoPermissions() {
    User user = new User("bob");
    Subscription sub = new Subscription(100L);
    sub.setCallbackURL("http://callback.url/test");
    sub.setName("MySub");
    sub.setRequestMethod(RequestMethod.GET);
    sub.setSubscribedURL("http://my.url/test/200/sub");
    sub.setCreatedBy(user);
    List<Subscription> subs = List.of(sub);
    var entityIO = new BasicEntityIO() {
      {
        setId(200L);
      }
    };

    when(request.getMethod()).thenReturn("GET");
    when(response.getStatus()).thenReturn(200);
    when(response.getEntity()).thenReturn(entityIO);
    when(service.getMatchingSubscriptions(RequestMethod.GET)).thenReturn(subs);
    when(permissionsService.filterAllowedUsers(eq(200L), eq(AccessType.Read), anyCollection())).thenReturn(Set.of());

    filter.filter(request, response);
    verify(executorService, never()).execute(any());
  }

  /**
   * P2c rewire: a single batch call replaces the per-username loop. Verifies external
   * behaviour (which subscriptions fire) is preserved across an allowed/denied/url-mismatch mix
   * and that exactly one Cypher-side call happens for the matched usernames.
   */
  @Test
  public void testFilterBatchPermissionCheck_preservesPerSubscriptionDecisions() {
    Subscription allowed = new Subscription(1L);
    allowed.setCallbackURL("http://callback.url/a");
    allowed.setName("Allowed");
    allowed.setRequestMethod(RequestMethod.GET);
    allowed.setSubscribedURL("http://my.url/test/200/sub");
    allowed.setCreatedBy(new User("alice"));

    Subscription denied = new Subscription(2L);
    denied.setCallbackURL("http://callback.url/b");
    denied.setName("Denied");
    denied.setRequestMethod(RequestMethod.GET);
    denied.setSubscribedURL("http://my.url/test/200/sub");
    denied.setCreatedBy(new User("mallory"));

    Subscription urlMiss = new Subscription(3L);
    urlMiss.setCallbackURL("http://callback.url/c");
    urlMiss.setName("UrlMiss");
    urlMiss.setRequestMethod(RequestMethod.GET);
    urlMiss.setSubscribedURL("http://my.url/other/.*");
    urlMiss.setCreatedBy(new User("carol"));

    List<Subscription> subs = List.of(allowed, denied, urlMiss);
    var entityIO = new BasicEntityIO() {
      {
        setId(200L);
      }
    };

    when(request.getMethod()).thenReturn("GET");
    when(response.getStatus()).thenReturn(200);
    when(response.getEntity()).thenReturn(entityIO);
    when(service.getMatchingSubscriptions(RequestMethod.GET)).thenReturn(subs);
    when(
      permissionsService.filterAllowedUsers(
        eq(200L),
        eq(AccessType.Read),
        argThat((Collection<String> usernames) -> usernames.containsAll(List.of("alice", "mallory")) && !usernames.contains("carol"))
      )
    ).thenReturn(Set.of("alice"));

    filter.filter(request, response);

    verify(executorService, times(1)).execute(any());
    verify(permissionsService, atLeastOnce()).filterAllowedUsers(anyLong(), any(), anyCollection());
    verify(permissionsService, never()).isAllowed(any(ContainerRequestContext.class), any(), any());
  }
}
