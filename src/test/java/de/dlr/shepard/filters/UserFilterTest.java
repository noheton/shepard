package de.dlr.shepard.filters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.Principal;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.services.UserService;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.security.JWTPrincipal;
import jakarta.ws.rs.container.ContainerRequestContext;

public class UserFilterTest extends BaseTestCase {

	@Mock
	private ContainerRequestContext request;

	@Mock
	private UserService service;

	@Mock
	private GracePeriodUtil<?> lastSeen;

	@Spy
	private UserFilter filter;

	@Captor
	private ArgumentCaptor<JWTPrincipal> userCaptor;

	@BeforeEach
	public void prepareSpy() throws IllegalAccessException {
		when(filter.getService()).thenReturn(service);
		FieldUtils.writeField(filter, "lastSeen", lastSeen, true);
	}

	@Test
	public void testFilter_Successful() throws IOException {
		Principal p = new JWTPrincipal("MyUser", "MyKeyId");

		when(request.getProperty("user")).thenReturn(p);

		filter.filter(request);
		verify(service).updateUser((JWTPrincipal) p);
		verify(lastSeen).elementSeen("MyUser", null);
	}

	@Test
	public void testFilter_GracePeriod() throws IOException {
		Principal p = new JWTPrincipal("MyUser", "MyKeyId");

		when(request.getProperty("user")).thenReturn(p);
		when(lastSeen.elementIsKnown("MyUser")).thenReturn(true);

		filter.filter(request);
		verify(service, never()).updateUser((JWTPrincipal) p);
	}

	@Test
	public void testFilter_NoProperty() throws IOException {
		when(request.getProperty("user")).thenReturn(null);

		filter.filter(request);
		verify(service, never()).updateUser(any());
	}

	@Test
	public void testFilter_InvalidProperty() throws IOException {
		Principal p = new Principal() {

			@Override
			public String getName() {
				return "myName";
			}
		};

		when(request.getProperty("user")).thenReturn(p);

		filter.filter(request);
		verify(service, never()).updateUser(any());
	}

}
