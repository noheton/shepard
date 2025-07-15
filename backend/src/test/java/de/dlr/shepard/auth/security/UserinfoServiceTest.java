package de.dlr.shepard.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.exceptions.ShepardProcessingException;
import io.vertx.core.cli.Argument;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.net.URI;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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
  private ArgumentCaptor<URI> uriCaptor;

  @Captor
  private ArgumentCaptor<String> urlCaptor;

  @Captor
  private ArgumentCaptor<String> headerCaptor;

  private static MockedStatic<ConfigProvider> mockConfigProvider;

  @BeforeAll
  public static void mockConfigProvider() {
    Config config = mock(Config.class);
    mockConfigProvider = Mockito.mockStatic(ConfigProvider.class);
    mockConfigProvider.when(ConfigProvider::getConfig).thenReturn(config);
    when(config.getValue("oidc.authority", String.class)).thenReturn("https://my.oidc.provider.com/realm/");
  }

  @AfterAll
  public static void removeMockConfigProvider() {
    mockConfigProvider.close();
  }

  @BeforeEach
  public void prepareSpy() throws IllegalAccessException {
    FieldUtils.writeField(service, "client", client, true);
  }

  @BeforeEach
  public void setUpClient() throws IllegalAccessException {
    doReturn(target).when(client).target(uriCaptor.capture());
    doReturn(target).when(client).target(urlCaptor.capture());
    when(target.request(MediaType.APPLICATION_JSON)).thenReturn(builder);
    doReturn(builder).when(builder).header(eq(HttpHeaders.AUTHORIZATION), headerCaptor.capture());
    when(builder.buildGet()).thenReturn(invocation);
  }

  @Test
  public void testFetchUser_Successful() throws IllegalAccessException {
    var userinfo = new Userinfo("f:sub:name_fi", "first name", "first.name@example.com", "first", "name", "name_fi");

    FieldUtils.writeField(service, "userinfoEndpoint", "https://userinfo.endpoint/userinfo", true);
    when(invocation.invoke(Userinfo.class)).thenReturn(userinfo);
    var actual = service.fetchUserinfo("Bearer myToken");
    assertEquals(userinfo, actual);
    assertEquals("https://userinfo.endpoint/userinfo", urlCaptor.getValue());
    assertEquals("Bearer myToken", headerCaptor.getValue());
  }

  @Test
  public void testFetchUser_InvokeInit() throws IllegalAccessException {
    var userinfo = new Userinfo("f:sub:name_fi", "first name", "first.name@example.com", "first", "name", "name_fi");

    doAnswer(
      new Answer<>() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          FieldUtils.writeField(service, "userinfoEndpoint", "https://userinfo.endpoint/userinfo", true);
          return null;
        }
      }
    )
      .when(service)
      .init();
    when(invocation.invoke(Userinfo.class)).thenReturn(userinfo);

    var actual = service.fetchUserinfo("Bearer myToken");
    assertEquals(userinfo, actual);
    verify(service).init();
  }

  @Test
  public void testInit_Successful() {
    var conf = new OpenIdConfiguration(
      "iss",
      "auth",
      "userinfo.endpoint",
      "jwks",
      new String[0],
      new String[0],
      new String[0]
    );

    when(invocation.invoke(OpenIdConfiguration.class)).thenReturn(conf);

    service.init();
    assertEquals(
      URI.create("https://my.oidc.provider.com/realm/.well-known/openid-configuration"),
      uriCaptor.getValue()
    );
    assertTrue(headerCaptor.getAllValues().isEmpty());
  }

  @Test
  public void testInit_ReturnNull() {
    when(invocation.invoke(OpenIdConfiguration.class)).thenReturn(null);

    assertThrows(ShepardProcessingException.class, service::init);
  }

  @Test
  public void testInit_ProcessingException() {
    when(invocation.invoke(OpenIdConfiguration.class)).thenThrow(new ProcessingException("Message"));

    assertThrows(ShepardProcessingException.class, service::init);
  }

  @Test
  public void testFetchUser_ReturnNull() throws IllegalAccessException {
    FieldUtils.writeField(service, "userinfoEndpoint", "https://userinfo.endpoint/userinfo", true);
    when(invocation.invoke(Userinfo.class)).thenReturn(null);

    assertThrows(ShepardProcessingException.class, () -> service.fetchUserinfo("Bearer myToken"));
  }

  @Test
  public void testFetchUser_ProcessingException() throws IllegalAccessException {
    FieldUtils.writeField(service, "userinfoEndpoint", "https://userinfo.endpoint/userinfo", true);
    when(invocation.invoke(Userinfo.class)).thenThrow(new ProcessingException("Message"));

    assertThrows(ShepardProcessingException.class, () -> service.fetchUserinfo("Bearer myToken"));
  }
}
