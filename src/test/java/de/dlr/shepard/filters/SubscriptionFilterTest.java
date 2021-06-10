package de.dlr.shepard.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.Executor;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.io.AbstractEntityIO;
import de.dlr.shepard.neo4Core.services.SubscriptionService;
import de.dlr.shepard.util.RequestMethod;

public class SubscriptionFilterTest extends BaseTestCase {

	@Mock
	private UriInfo uriInfo;

	@Mock
	private ContainerRequestContext request;

	@Mock
	private ContainerResponseContext response;

	@Mock
	private SubscriptionService service;

	@Mock
	private Executor executor;

	private SubscriptionFilter filter;

	@BeforeEach
	public void setUpUriInfo() throws URISyntaxException {
		URI uri = new URI("http://my.url/test");
		when(uriInfo.getAbsolutePath()).thenReturn(uri);
		when(request.getUriInfo()).thenReturn(uriInfo);
	}

	@BeforeEach
	public void prepareSpy() {
		filter = spy(new SubscriptionFilter(executor));
		when(filter.getService()).thenReturn(service);
	}

	@Test
	public void testFilterSucessful() {
		Subscription sub = new Subscription(100L);
		sub.setCallbackURL("http://callback.url/test");
		sub.setName("MySub");
		sub.setRequestMethod(RequestMethod.GET);
		sub.setSubscribedURL("http://my.url/test");
		List<Subscription> subs = List.of(sub);
		AbstractEntityIO entity = new AbstractEntityIO() {
			{
				setId(200L);
			}
		};

		when(request.getMethod()).thenReturn(RequestMethod.GET.name());
		when(response.getStatus()).thenReturn(200);
		when(response.getEntity()).thenReturn(entity);
		when(service.getMatchingSubscriptions(RequestMethod.GET)).thenReturn(subs);

		filter.filter(request, response);
		verify(executor).execute(any());
	}

	@Test
	public void testFilterNoId() {
		Subscription sub = new Subscription();
		sub.setCallbackURL("http://callback.url/test");
		sub.setId(123L);
		sub.setName("MySub");
		sub.setRequestMethod(RequestMethod.GET);
		sub.setSubscribedURL("http://my.url/test");
		List<Subscription> subs = List.of(sub);
		Object noId = new Object();

		when(request.getMethod()).thenReturn(RequestMethod.GET.name());
		when(response.getStatus()).thenReturn(200);
		when(response.getEntity()).thenReturn(noId);
		when(service.getMatchingSubscriptions(RequestMethod.GET)).thenReturn(subs);

		filter.filter(request, response);
		verify(executor).execute(any());
	}

	@Test
	public void testFilterNoMatch() {
		Subscription sub = new Subscription();
		sub.setCallbackURL("http://callback.url/test");
		sub.setId(123L);
		sub.setName("MySub");
		sub.setRequestMethod(RequestMethod.GET);
		sub.setSubscribedURL("http://my.url/test2");
		List<Subscription> subs = List.of(sub);
		Object noId = new Object();

		when(request.getMethod()).thenReturn(RequestMethod.GET.name());
		when(response.getStatus()).thenReturn(200);
		when(response.getEntity()).thenReturn(noId);
		when(service.getMatchingSubscriptions(RequestMethod.GET)).thenReturn(subs);

		filter.filter(request, response);
		verify(executor, never()).execute(any());
	}

	@Test
	public void testFilterBadResponseBelow() {
		Subscription sub = new Subscription();
		sub.setCallbackURL("http://callback.url/test");
		sub.setId(123L);
		sub.setName("MySub");
		sub.setRequestMethod(RequestMethod.GET);
		sub.setSubscribedURL("http://my.url/test");
		List<Subscription> subs = List.of(sub);
		Object noId = new Object();

		when(request.getMethod()).thenReturn(RequestMethod.GET.name());
		when(response.getStatus()).thenReturn(100);
		when(response.getEntity()).thenReturn(noId);
		when(service.getMatchingSubscriptions(RequestMethod.GET)).thenReturn(subs);

		filter.filter(request, response);
		verify(executor, never()).execute(any());
	}

	@Test
	public void testFilterBadResponseAbove() {
		Subscription sub = new Subscription();
		sub.setCallbackURL("http://callback.url/test");
		sub.setId(123L);
		sub.setName("MySub");
		sub.setRequestMethod(RequestMethod.GET);
		sub.setSubscribedURL("http://my.url/test");
		List<Subscription> subs = List.of(sub);
		Object noId = new Object();

		when(request.getMethod()).thenReturn(RequestMethod.GET.name());
		when(response.getStatus()).thenReturn(400);
		when(response.getEntity()).thenReturn(noId);
		when(service.getMatchingSubscriptions(RequestMethod.GET)).thenReturn(subs);

		filter.filter(request, response);
		verify(executor, never()).execute(any());
	}

}
