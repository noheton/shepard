package de.dlr.shepard.integrationtests;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;

public class WireMockResource implements QuarkusTestResourceLifecycleManager {

  private static WireMockServer wireMockServer;

  private static final String SELECT_TEMPLATE =
    """
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

    SELECT DISTINCT ?o WHERE {
        ?s rdfs:label ?o .
        FILTER ( ?s = <%s> )
    }""";

  @Override
  public Map<String, String> start() {
    wireMockServer = new WireMockServer(options().dynamicPort());
    wireMockServer.start();

    wireMockServer.stubFor(
      // stub for health check on: https://dbpedia.org/sparql/
      get(urlPathEqualTo("/sparql"))
        .withQueryParam("query", equalTo("ASK { ?x ?y ?z }"))
        .willReturn(aResponse().withStatus(200).withBody("{ \"head\": {\"link\": []  }, \"boolean\": true }"))
    );
    wireMockServer.stubFor(
      // stub for PropertyIRI SparqlConnector.request_term
      get(urlPathEqualTo("/sparql"))
        .withQueryParam("query", equalTo(SELECT_TEMPLATE.formatted("http://dbpedia.org/ontology/ingredient")))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              "{\"head\":{\"link\":[],\"vars\":[\"o\"]},\"results\":{\"distinct\":false,\"ordered\":true,\"bindings\":[{\"o\":{\"type\":\"literal\",\"xml:lang\":\"de\",\"value\":\"Zutat\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"en\",\"value\":\"ingredient\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"fr\",\"value\":\"ingrédient\"}}]}}"
            )
        )
    );
    // stub for ValueIRI SparqlConnector.request_term
    wireMockServer.stubFor(
      get(urlPathEqualTo("/sparql"))
        .withQueryParam("query", equalTo(SELECT_TEMPLATE.formatted("http://dbpedia.org/resource/Almond_milk")))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              "{\"head\":{\"link\":[],\"vars\":[\"o\"]},\"results\":{\"distinct\":false,\"ordered\":true,\"bindings\":[{\"o\":{\"type\":\"literal\",\"xml:lang\":\"en\",\"value\":\"Almond milk\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"ar\",\"value\":\"حليب اللوز\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"ca\",\"value\":\"Llet d'ametlla\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"de\",\"value\":\"Mandelmilch\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"el\",\"value\":\"Γάλα αμυγδάλου\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"eo\",\"value\":\"Migdallakto\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"es\",\"value\":\"Leche de almendra\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"fr\",\"value\":\"Lait d'amande\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"in\",\"value\":\"Sari kacang almond\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"it\",\"value\":\"Latte di mandorla\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"ko\",\"value\":\"아몬드밀크\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"ja\",\"value\":\"アーモンドミルク\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"nl\",\"value\":\"Amandelmelk\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"pl\",\"value\":\"Mleko migdałowe\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"pt\",\"value\":\"Leite-de-amêndoa\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"ru\",\"value\":\"Миндальное молоко\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"sv\",\"value\":\"Mandelmjölk\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"uk\",\"value\":\"Мигдалеве молоко\"}},{\"o\":{\"type\":\"literal\",\"xml:lang\":\"zh\",\"value\":\"扁桃仁奶\"}}]}}"
            )
        )
    );

    return new HashMap<>();
  }

  @Override
  public synchronized void stop() {
    if (wireMockServer != null) {
      wireMockServer.stop();
      wireMockServer = null;
    }
  }

  public static String getWireMockServerURlWithPath(String path) {
    return wireMockServer.url(path);
  }
}
