package de.dlr.shepard.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.neo4Core.services.SubscriptionService;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.RequestMethod;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
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
  PermissionsUtil permissionsUtil;

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
    when(permissionsUtil.isAllowed(uriInfo.getPathSegments(), AccessType.Read, "bob")).thenReturn(true);

    filter.filter(request, response);
    verify(executorService).execute(any());
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
    when(permissionsUtil.isAllowed(uriInfo.getPathSegments(), AccessType.Read, "bob")).thenReturn(true);

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
    when(permissionsUtil.isAllowed(uriInfo.getPathSegments(), AccessType.Read, "bob")).thenReturn(false);

    filter.filter(request, response);
    verify(executorService, never()).execute(any());
  }
}
