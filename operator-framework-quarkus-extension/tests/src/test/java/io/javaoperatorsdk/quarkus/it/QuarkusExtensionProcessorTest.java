package io.javaoperatorsdk.quarkus.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.utils.ResponseProviders;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesMockServerTestResource;
import io.quarkus.test.kubernetes.client.MockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This tests creates and starts an application accessed over REST to assess that injected values
 * are present and what we expect.
 */
@QuarkusTest
@QuarkusTestResource(KubernetesMockServerTestResource.class)
public class QuarkusExtensionProcessorTest {

  @MockServer KubernetesMockServer server;

  @BeforeEach
  public void before() {
    server
        .expect()
        .get()
        .withPath(
            "/apis/apiextensions.k8s.io/v1/customresourcedefinitions/testresources.example.com")
        .andReply(ResponseProviders.of(200, new CustomResourceDefinitionBuilder().build()))
        .always();
    // this allows the websocket watch connector to retry a thousand times and get us through this
    // test
    // it would be better to fake a websocket, naturally
    server
        .expect()
        .get()
        .withPath("/apis/example.com/v1/testresources?watch=true")
        .andReply(ResponseProviders.of(200, ""))
        .always();
  }

  @Test
  void controllerShouldExist() {
    // first check that we're not always returning true for any controller name :)
    given().when().get("/operator/does_not_exist").then().statusCode(200).body(is("false"));

    // given the name of the TestController, the app should reply true meaning that it is indeed
    // injected
    given().when().get("/operator/" + TestController.NAME).then().statusCode(200).body(is("true"));
  }

  @Test
  void controllerIsRegistered() {
    // make sure this registration is delayed
    given()
        .when()
        .get("/operator/registered/" + TestController.NAME)
        .then()
        .statusCode(200)
        .body(is("false"));
    // this one is not
    given()
        .when()
        .get("/operator/registered/" + ConfiguredController.NAME)
        .then()
        .statusCode(200)
        .body(is("true"));
    // now trigger registration
    given().when().post("/operator/register").then().statusCode(204);
    // and check that it worked
    given()
        .when()
        .get("/operator/registered/" + TestController.NAME)
        .then()
        .statusCode(200)
        .body(is("true"));
  }

  @Test
  void configurationForControllerShouldExist() {
    // check that the config for the test controller can be retrieved and is conform to our
    // expectations
    final var resourceName = TestResource.class.getCanonicalName();
    given()
        .when()
        .get("/operator/" + TestController.NAME + "/config")
        .then()
        .statusCode(200)
        .body(
            "customResourceClass", equalTo(resourceName),
            "name", equalTo(TestController.NAME));
  }

  @Test
  void applicationPropertiesShouldOverrideDefaultAndAnnotation() {
    given()
        .when()
        .get("/operator/" + ConfiguredController.NAME + "/config")
        .then()
        .statusCode(200)
        .body(
            "finalizer", equalTo("from-property/finalizer"),
            "namespaces", hasItem("bar"));
  }
}
