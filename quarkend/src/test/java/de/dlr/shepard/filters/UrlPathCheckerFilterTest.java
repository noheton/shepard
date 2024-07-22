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
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;

public class UrlPathCheckerFilterTest extends BaseTestCase {

  @Mock
  private ContainerRequestContext request;

  @Mock
  private UriInfo uriInfo;

  @Mock
  private UrlPathChecker checker;

  @Spy
  private UrlPathCheckerFilter filter;

  @Captor
  private ArgumentCaptor<Response> responseCaptor;

  @BeforeEach
  public void prepareSpy() {
    when(filter.getUrlPathChecker()).thenReturn(checker);
    when(request.getUriInfo()).thenReturn(uriInfo);
  }

  @Test
  public void testFilter_Successful() throws IOException {
    PathSegment segment = mock(PathSegment.class);
    var segments = List.of(segment);
    when(uriInfo.getPathSegments()).thenReturn(segments);
    doNothing().when(checker).checkPathSegments(segments);

    filter.filter(request);
    verify(checker).checkPathSegments(segments);
  }

  @Test
  public void testFilter_badPath() throws IOException {
    PathSegment segment = mock(PathSegment.class);
    var segments = List.of(segment);
    when(uriInfo.getPathSegments()).thenReturn(segments);
    doThrow(new InvalidPathException("MyError")).when(checker).checkPathSegments(segments);

    filter.filter(request);
    verify(request).abortWith(responseCaptor.capture());
    verify(checker).checkPathSegments(segments);
    assertEquals(404, responseCaptor.getValue().getStatus());
  }
}
