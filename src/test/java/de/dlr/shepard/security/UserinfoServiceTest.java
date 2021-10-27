package de.dlr.shepard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.ProcessingException;
import de.dlr.shepard.neo4Core.entities.User;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

public class UserinfoServiceTest extends BaseTestCase {

	@Mock
	private Client client;

	@Mock
	private WebTarget target;

	@Mock
	private Builder builder;

	@Mock
	private Invocation invocation;

	@Spy
	private UserinfoService service;

	@Captor
	private ArgumentCaptor<String> urlCaptor;

	@Captor
	private ArgumentCaptor<String> headerCaptor;

	@BeforeEach
	public void prepareSpy() throws IllegalAccessException {
		var oidcConfidurationUrl = "https://my.oidc.provider.com/realm/.well-known/openid-configuration";
		FieldUtils.writeField(service, "oidcConfidurationUrl", oidcConfidurationUrl, true);
		FieldUtils.writeField(service, "client", client, true);
	}

	@BeforeEach
	public void setUpClient() throws IllegalAccessException {
		doReturn(target).when(client).target(urlCaptor.capture());
		when(target.request(MediaType.APPLICATION_JSON)).thenReturn(builder);
		doReturn(builder).when(builder).header(eq(HttpHeaders.AUTHORIZATION), headerCaptor.capture());
		when(builder.buildGet()).thenReturn(invocation);
	}

	@Test
	public void testFetchUser_Successful() throws ProcessingException, IllegalAccessException {
		var user = new User("name_fi", "first", "name", "first.name@example.com");
		var userinfo = new Userinfo("f:sub:name_fi", "first name", "first.name@example.com", "first", "name",
				"name_fi");

		FieldUtils.writeField(service, "userinfoEndpoint", "https://userinfo.endpoint/userinfo", true);
		when(invocation.invoke(Userinfo.class)).thenReturn(userinfo);

		var actual = service.fetchUserinfo("Bearer myToken");
		assertEquals(user, actual);
		assertEquals("https://userinfo.endpoint/userinfo", urlCaptor.getValue());
		assertEquals("Bearer myToken", headerCaptor.getValue());
	}

	@Test
	public void testFetchUser_DifferentSubject() throws ProcessingException, IllegalAccessException {
		var user = new User("name_fi", "first", "name", "first.name@example.com");
		var userinfo = new Userinfo("name_fi", "first name", "first.name@example.com", "first", "name", "name_fi");

		FieldUtils.writeField(service, "userinfoEndpoint", "https://userinfo.endpoint/userinfo", true);
		when(invocation.invoke(Userinfo.class)).thenReturn(userinfo);

		var actual = service.fetchUserinfo("Bearer myToken");
		assertEquals(user, actual);
		assertEquals("https://userinfo.endpoint/userinfo", urlCaptor.getValue());
		assertEquals("Bearer myToken", headerCaptor.getValue());
	}

	@Test
	public void testFetchUser_InvokeInit() throws ProcessingException, IllegalAccessException {
		var user = new User("name_fi", "first", "name", "first.name@example.com");
		var userinfo = new Userinfo("f:sub:name_fi", "first name", "first.name@example.com", "first", "name",
				"name_fi");

		doAnswer(new Answer<>() {
			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				FieldUtils.writeField(service, "userinfoEndpoint", "https://userinfo.endpoint/userinfo", true);
				return null;
			}
		}).when(service).init();
		when(invocation.invoke(Userinfo.class)).thenReturn(userinfo);

		var actual = service.fetchUserinfo("Bearer myToken");
		assertEquals(user, actual);
		verify(service).init();
	}

	@Test
	public void testInit_Successful() throws ProcessingException {
		var conf = new OpenIdConfiguration("iss", "auth", "userinfo.endpoint", "jwks", new String[0], new String[0],
				new String[0]);

		when(invocation.invoke(OpenIdConfiguration.class)).thenReturn(conf);

		service.init();
		assertEquals("https://my.oidc.provider.com/realm/.well-known/openid-configuration", urlCaptor.getValue());
		assertTrue(headerCaptor.getAllValues().isEmpty());
	}

	@Test
	public void testInit_ReturnNull() throws ProcessingException {
		when(invocation.invoke(OpenIdConfiguration.class)).thenReturn(null);

		assertThrows(ProcessingException.class, service::init);
	}

	@Test
	public void testInit_ProcessingException() throws ProcessingException {
		when(invocation.invoke(OpenIdConfiguration.class)).thenThrow(new jakarta.ws.rs.ProcessingException("Message"));

		assertThrows(ProcessingException.class, service::init);
	}

	@Test
	public void testFetchUser_ReturnNull() throws ProcessingException, IllegalAccessException {
		FieldUtils.writeField(service, "userinfoEndpoint", "https://userinfo.endpoint/userinfo", true);
		when(invocation.invoke(Userinfo.class)).thenReturn(null);

		assertThrows(ProcessingException.class, () -> service.fetchUserinfo("Bearer myToken"));
	}

	@Test
	public void testFetchUser_ProcessingException() throws ProcessingException, IllegalAccessException {
		FieldUtils.writeField(service, "userinfoEndpoint", "https://userinfo.endpoint/userinfo", true);
		when(invocation.invoke(Userinfo.class)).thenThrow(new jakarta.ws.rs.ProcessingException("Message"));

		assertThrows(ProcessingException.class, () -> service.fetchUserinfo("Bearer myToken"));
	}

}
