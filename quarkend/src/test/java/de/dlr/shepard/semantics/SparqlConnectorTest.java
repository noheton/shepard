package de.dlr.shepard.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SparqlConnectorTest extends BaseTestCase {

  @Mock
  private Client client;

  @Mock
  private WebTarget webTarget;

  @Mock
  private Builder builder;

  @Mock
  private Invocation invocation;

  @Mock
  private Response response;

  @InjectMocks
  private SparqlConnector connector = new SparqlConnector("endpoint");

  private final String query =
    """
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

    SELECT DISTINCT ?o WHERE {
        ?s rdfs:label ?o .
        FILTER ( ?s = <http://example.com> )
    }""";
  private final String result =
    """
    {
       "results":{
          "bindings":[
             {
                "o":{
                   "type":"label",
                   "value":"kelvin"
                }
             },
             {
                "o":{
                   "type":"label",
                   "xml:lang":"en",
                   "value":"kelvin@en"
                }
             },
             {
                "o":{
                   "type":"label",
                   "xml:lang":"de",
                   "value":"kelvin@de"
                }
             }
          ]
       }
    }
    """;
  private final String resultOBlank =
    """
    {
       "results":{
          "bindings":[
             {
                "o":{
                   "type":"bla",
                   "value":""
                }
             }
          ]
       }
    }
    """;

  private final String askQuery = "ASK { ?x ?y ?z }";
  private final String askResult =
    """
    {
      "head": { "link": [] },
      "boolean": true
    }
    """;
  private final String resultBooleanBlank =
    """
    {
      "head": { "link": [] },
      "boolean": ""
    }
    """;
  private final String resultBooleanFalse =
    """
    {
      "head": { "link": [] },
      "boolean": false
    }
    """;

  private final String resultInvalid =
    """
    {
      "invalid": "JSON"
    }
    """;

  @Test
  public void getTermTest() {
    when(client.target("endpoint")).thenReturn(webTarget);
    when(webTarget.queryParam("query", query)).thenReturn(webTarget);
    when(webTarget.request(MediaType.APPLICATION_JSON)).thenReturn(builder);
    when(builder.buildGet()).thenReturn(invocation);
    when(invocation.invoke()).thenReturn(response);
    when(response.readEntity(String.class)).thenReturn(result);

    var actual = connector.getTerm("http://example.com");

    assertEquals(Map.of("", "kelvin", "de", "kelvin@de", "en", "kelvin@en"), actual);
    verify(client).close();
  }

  @Test
  public void getTermTest_RequestFailsException() {
    when(client.target("endpoint")).thenReturn(webTarget);
    when(webTarget.queryParam("query", query)).thenReturn(webTarget);
    when(webTarget.request(MediaType.APPLICATION_JSON)).thenReturn(builder);
    when(builder.buildGet()).thenReturn(invocation);
    doThrow(ProcessingException.class).when(invocation).invoke();

    var actual = connector.getTerm("http://example.com");

    assertEquals(Collections.emptyMap(), actual);
    verify(client).close();
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "  ", "No JSON", resultInvalid, resultOBlank })
  public void getTermTest_RequestFailsBlank(String requestResult) {
    when(client.target("endpoint")).thenReturn(webTarget);
    when(webTarget.queryParam("query", query)).thenReturn(webTarget);
    when(webTarget.request(MediaType.APPLICATION_JSON)).thenReturn(builder);
    when(builder.buildGet()).thenReturn(invocation);
    when(invocation.invoke()).thenReturn(response);
    when(response.readEntity(String.class)).thenReturn(requestResult);

    var actual = connector.getTerm("http://example.com");

    assertEquals(Collections.emptyMap(), actual);
    verify(client).close();
  }

  @Test
  public void healthCheckTest() {
    when(client.target("endpoint")).thenReturn(webTarget);
    when(webTarget.queryParam("query", askQuery)).thenReturn(webTarget);
    when(webTarget.request(MediaType.APPLICATION_JSON)).thenReturn(builder);
    when(builder.buildGet()).thenReturn(invocation);
    when(invocation.invoke()).thenReturn(response);
    when(response.readEntity(String.class)).thenReturn(askResult);

    var actual = connector.healthCheck();

    assertTrue(actual);
    verify(client).close();
  }

  @ParameterizedTest
  @ValueSource(strings = { resultInvalid, resultBooleanBlank, resultBooleanFalse })
  public void healthCheckTest_False(String requestResult) {
    when(client.target("endpoint")).thenReturn(webTarget);
    when(webTarget.queryParam("query", askQuery)).thenReturn(webTarget);
    when(webTarget.request(MediaType.APPLICATION_JSON)).thenReturn(builder);
    when(builder.buildGet()).thenReturn(invocation);
    when(invocation.invoke()).thenReturn(response);
    when(response.readEntity(String.class)).thenReturn(requestResult);

    var actual = connector.healthCheck();

    assertFalse(actual);
    verify(client).close();
  }
}
