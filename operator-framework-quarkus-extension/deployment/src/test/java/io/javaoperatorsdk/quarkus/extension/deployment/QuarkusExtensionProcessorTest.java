package io.javaoperatorsdk.quarkus.extension.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import io.javaoperatorsdk.quarkus.it.TestController;
import io.javaoperatorsdk.quarkus.it.TestOperatorApp;
import io.javaoperatorsdk.quarkus.it.TestResource;
import io.quarkus.test.QuarkusProdModeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * This tests creates an application based on the application code found in the {@code
 * operator-framework-quarkus-tests-support} module. The app is started and accessed over REST to
 * assess that injected values are present and what we expect.
 */
public class QuarkusExtensionProcessorTest {

  @RegisterExtension
  static final QuarkusProdModeTest config =
      new QuarkusProdModeTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(TestOperatorApp.class, TestController.class))
          .setApplicationName("basic-app")
          .setApplicationVersion("0.1-SNAPSHOT")
          .setRun(true);

  @Test
  void controllerShouldExist() {
    // first check that we're not always returning true for any controller name :)
    given().when().get("/operator/does_not_exist").then().statusCode(200).body(is("false"));

    // given the name of the TestController, the app should reply true meaning that it is indeed
    // injected
    given().when().get("/operator/" + TestController.NAME).then().statusCode(200).body(is("true"));
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
            "doneableClass", equalTo(resourceName + "Doneable"),
            "name", equalTo(TestController.NAME),
            "crdName", equalTo(TestController.CRD_NAME));
  }
}
