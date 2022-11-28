package de.dlr.shepard.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public class SparqlConnectorTest extends BaseTestCase {

	@Mock
	private Client client;
	@Mock
	private WebTarget webTarget;
	@Mock
	private Builder builder;
	@Mock
	private Response response;

	@InjectMocks
	private SparqlConnector connector = new SparqlConnector("endpoint");

	private final String query = URLEncoder.encode("""
			SELECT DISTINCT * WHERE {
			    ?s ?p ?o .
			    FILTER ( ?s = <http://example.com> )
			}""", StandardCharsets.UTF_8).replace("+", "%20");
	private final String result = """
			{
			   "results":{
			      "bindings":[
			         {
			            "s":{
			               "type":"uri",
			               "value":"http://example.com/"
			            },
			            "p":{
			               "type":"test",
			               "value":"type"
			            },
			            "o":{
			               "type":"bla",
			               "value":"class"
			            }
			         },
			         {
			            "s":{
			               "type":"uri",
			               "value":"http://example.com/"
			            },
			            "p":{
			               "type":"test",
			               "value":"unit"
			            },
			            "o":{
			               "type":"bla",
			               "value":"kelvin"
			            }
			         }
			      ]
			   }
			}
			""";
	private final String resultPBlank = """
			{
			   "results":{
			      "bindings":[
			         {
			            "s":{
			               "type":"uri",
			               "value":"http://example.com/"
			            },
			            "p":{
			               "type":"test",
			               "value":""
			            },
			            "o":{
			               "type":"bla",
			               "value":"kelvin"
			            }
			         }
			      ]
			   }
			}
			""";
	private final String resultOBlank = """
			{
			   "results":{
			      "bindings":[
			         {
			            "s":{
			               "type":"uri",
			               "value":"http://example.com/"
			            },
			            "p":{
			               "type":"test",
			               "value":"unit"
			            },
			            "o":{
			               "type":"bla",
			               "value":""
			            }
			         }
			      ]
			   }
			}
			""";
	private final String resultInvalid = """
			{
			  "invalid": "JSON"
			}
			""";

	@Test
	public void getTermTest() {
		when(client.target("endpoint")).thenReturn(webTarget);
		when(webTarget.queryParam("query", query)).thenReturn(webTarget);
		when(webTarget.request(MediaType.APPLICATION_JSON)).thenReturn(builder);
		when(builder.get()).thenReturn(response);
		when(response.readEntity(String.class)).thenReturn(result);

		var actual = connector.getTerm("http://example.com");

		assertEquals(Map.of("type", "class", "unit", "kelvin"), actual);
		verify(client).close();
	}

	@Test
	public void getTermTest_RequestFailsException() {
		when(client.target("endpoint")).thenReturn(webTarget);
		when(webTarget.queryParam("query", query)).thenReturn(webTarget);
		when(webTarget.request(MediaType.APPLICATION_JSON)).thenReturn(builder);
		doThrow(ProcessingException.class).when(builder).get();

		var actual = connector.getTerm("http://example.com");

		assertEquals(Collections.emptyMap(), actual);
		verify(client).close();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  ", "No JSON", resultInvalid, resultPBlank, resultOBlank })
	public void getTermTest_RequestFailsBlank(String requestResult) {
		when(client.target("endpoint")).thenReturn(webTarget);
		when(webTarget.queryParam("query", query)).thenReturn(webTarget);
		when(webTarget.request(MediaType.APPLICATION_JSON)).thenReturn(builder);
		when(builder.get()).thenReturn(response);
		when(response.readEntity(String.class)).thenReturn(requestResult);

		var actual = connector.getTerm("http://example.com");

		assertEquals(Collections.emptyMap(), actual);
		verify(client).close();
	}
}
