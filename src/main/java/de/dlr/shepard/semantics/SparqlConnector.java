package de.dlr.shepard.semantics;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SparqlConnector implements ISemanticRepositoryConnector {

	private final String selectTemplate = """
			SELECT DISTINCT * WHERE {
			    ?s ?p ?o .
			    FILTER ( ?s = <%s> )
			}""";

	private final String askTemplate = "ASK { ?x ?y ?z }";

	// TODO: Use DESCRIBE instead of SELECT
	// private final String describeTemplate = "DESCRIBE <%s>";

	private final String endpoint;
	private final ObjectMapper mapper = new ObjectMapper();
	private Client client = ClientBuilder.newClient();

	public SparqlConnector(String endpoint) {
		this.endpoint = endpoint;
	}

	@Override
	public boolean healthCheck() {
		var query = formatQuery(askTemplate);
		var invocation = client.target(endpoint).queryParam("query", query).request(MediaType.APPLICATION_JSON)
				.buildGet();
		String requestResult = request(invocation);
		client.close();

		return parseJson(requestResult).map(t -> t.get("boolean")).map(JsonNode::asBoolean).orElse(false);
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
		var query = formatQuery(String.format(selectTemplate, termIri));
		var invocation = client.target(endpoint).queryParam("query", query).request(MediaType.APPLICATION_JSON)
				.buildGet();
		String responseEntity = request(invocation);
		client.close();
		return responseEntity;
	}

	private Map<String, String> parseResult(String requestResult) {
		var bindings = parseJson(requestResult).map(t -> t.get("results")).map(r -> r.get("bindings"));

		if (bindings.isEmpty())
			return Collections.emptyMap();

		var result = new HashMap<String, String>();
		for (var binding : bindings.get()) {
			var oBinding = Optional.of(binding);
			var property = oBinding.map(b -> b.get("p")).map(p -> p.get("value")).map(JsonNode::asText).orElse("");
			var object = oBinding.map(b -> b.get("o")).map(p -> p.get("value")).map(JsonNode::asText).orElse("");
			if (!property.isBlank() && !object.isBlank())
				result.put(property, object);
		}
		return result;
	}

	private Optional<JsonNode> parseJson(String string) {
		JsonNode tree;
		try {
			tree = mapper.readTree(string);
		} catch (JsonProcessingException e) {
			return Optional.empty();
		}
		return Optional.of(tree);
	}

	private String formatQuery(String query) {
		// https://stackoverflow.com/a/1634293
		return URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/**
	 * You have to close the client yourself afterwards
	 *
	 * @param invocation
	 * @return Response as String or null
	 */
	private String request(Invocation invocation) {
		String responseEntity;
		try {
			var response = invocation.invoke();
			responseEntity = response.readEntity(String.class);
		} catch (ProcessingException e) {
			log.error("Could not execute request");
			return null;
		}
		return responseEntity;
	}

}
