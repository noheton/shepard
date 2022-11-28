package de.dlr.shepard.semantics;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SparqlConnector implements ISemanticRepositoryConnector {

	private final String template = """
			SELECT DISTINCT * WHERE {
			    ?s ?p ?o .
			    FILTER ( ?s = <%s> )
			}""";

	private final String endpoint;
	private final ObjectMapper mapper = new ObjectMapper();
	private Client client = ClientBuilder.newClient();

	public SparqlConnector(String endpoint) {
		this.endpoint = endpoint;
	}

	@Override
	public Map<String, String> getTerm(String termIri) {
		var requestResult = requestTerm(termIri);
		if (requestResult == null || requestResult.isBlank()) {
			log.error("Could not retrieve request result");
			return Collections.emptyMap();
		}

		return parseResult(requestResult);
	}

	private String requestTerm(String termIri) {
		// https://stackoverflow.com/a/1634293
		var query = URLEncoder.encode(String.format(template, termIri), StandardCharsets.UTF_8).replace("+", "%20");
		String responseEntity;

		try {
			var response = client.target(endpoint).queryParam("query", query).request(MediaType.APPLICATION_JSON).get();
			responseEntity = response.readEntity(String.class);
		} catch (ProcessingException e) {
			log.error("Could not execute request");
			return null;
		} finally {
			client.close();
		}
		return responseEntity;
	}

	private Map<String, String> parseResult(String requestResult) {
		JsonNode tree;
		try {
			tree = mapper.readTree(requestResult);
		} catch (JsonProcessingException e) {
			log.error("Could not parse request result");
			return Collections.emptyMap();
		}

		var result = new HashMap<String, String>();
		try {
			var results = tree.get("results");
			var bindings = results.get("bindings");
			for (var binding : bindings) {
				var property = binding.get("p").get("value").asText();
				var object = binding.get("o").get("value").asText();
				if (!property.isBlank() && !object.isBlank())
					result.put(property, object);
			}
		} catch (NullPointerException e) {
			log.error("Invalid request result");
			Collections.emptyMap();
		}
		return result;
	}

}
