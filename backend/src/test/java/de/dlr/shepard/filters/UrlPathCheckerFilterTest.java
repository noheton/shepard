package de.dlr.shepard.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidPathException;
import de.dlr.shepard.neo4Core.services.UrlPathChecker;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@QuarkusComponentTest
public class UrlPathCheckerFilterTest extends BaseTestCase {

  @InjectMock
  ContainerRequestContext request;

  @InjectMock
  UriInfo uriInfo;

  @InjectMock
  UrlPathChecker checker;

  @Inject
  UrlPathCheckerFilter filter;

  @Captor
  ArgumentCaptor<Response> responseCaptor;

  MultivaluedMap<String, String> queryParams;

  @BeforeEach
  public void prepareSpy() {
    when(request.getUriInfo()).thenReturn(uriInfo);
    queryParams = new MultivaluedHashMap<String, String>();
    when(request.getUriInfo().getQueryParameters()).thenReturn(queryParams);
  }

  @Test
  public void testFilter_Successful() throws IOException {
    PathSegment segment = mock(PathSegment.class);
    var segments = List.of(segment);
    when(uriInfo.getPathSegments()).thenReturn(segments);
    doNothing().when(checker).assertIfIdsAreValid(segments, queryParams);

    filter.filter(request);
    verify(checker).assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void testFilter_badPath() throws IOException {
    PathSegment segment = mock(PathSegment.class);
    var segments = List.of(segment);
    when(uriInfo.getPathSegments()).thenReturn(segments);
    doThrow(new InvalidPathException("MyError")).when(checker).assertIfIdsAreValid(segments, queryParams);

    filter.filter(request);
    verify(request).abortWith(responseCaptor.capture());
    verify(checker).assertIfIdsAreValid(segments, queryParams);
    assertEquals(404, responseCaptor.getValue().getStatus());
  }
}
