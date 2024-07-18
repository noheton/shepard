package de.dlr.shepard;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestHTTPEndpoint(GreetingResource.class)
class GreetingResourceTest {

  @Test
  void testHelloEndpoint() {
    given().when().get().then().statusCode(200).body(is("Hello from Quarkus REST"));
  }
}
