// Failsafe IT (needs docker-compose: Neo4j+MongoDB+TimescaleDB+PostGIS + running app on :8083); @QuarkusComponentTest can't observe the quarkus-cache Micrometer lifecycle.
package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.common.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
public class PermissionsServiceCacheMetricsIT extends BaseTestCaseIT {

  private static final String METRICS_PATH = "/metrics/prometheus";
  private static final String CACHE_NAME = "permissions-service-cache";

  @Test
  public void permissionsCacheEmitsGetsMeter() {
    var collectionsURL = "/" + Constants.COLLECTIONS;
    given().spec(requestSpecOfDefaultUser).when().get(collectionsURL).then().statusCode(200);
    given().spec(requestSpecOfDefaultUser).when().get(collectionsURL).then().statusCode(200);
    given().spec(requestSpecOfDefaultUser).when().get(collectionsURL).then().statusCode(200);

    RequestSpecification metricsSpec = RestAssured.given().baseUri(host).port(port).basePath("/");
    String body = metricsSpec.when().get(METRICS_PATH).then().statusCode(200).extract().asString();

    double gets = sumPrometheusSamples(body, "cache_gets_total", CACHE_NAME);
    if (gets == 0d) {
      gets = sumPrometheusSamples(body, "cache_gets", CACHE_NAME);
    }

    assertThat(gets).as("cache.gets samples for cache=%s", CACHE_NAME).isGreaterThan(0d);
  }

  private static double sumPrometheusSamples(String body, String metric, String cacheTag) {
    Pattern line = Pattern.compile(
      "^" + Pattern.quote(metric) + "\\{[^}]*cache=\"" + Pattern.quote(cacheTag) + "\"[^}]*}\\s+([\\d.eE+-]+)",
      Pattern.MULTILINE
    );
    Matcher m = line.matcher(body);
    double sum = 0d;
    while (m.find()) {
      try {
        sum += Double.parseDouble(m.group(1));
      } catch (NumberFormatException ignored) {
        // ignore non-numeric samples
      }
    }
    return sum;
  }
}
