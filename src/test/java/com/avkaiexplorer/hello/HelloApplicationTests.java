package com.avkaiexplorer.hello;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.containsString;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** API tests over real HTTP against the running application (RestAssured) with an in-memory database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloApplicationTests {

	@Value("${local.server.port}")
	int port;

	@BeforeEach
	void setUp() {
		RestAssured.port = port;
	}

	@Test
	void pingReturnsTheSeededMessage() {
		given().when().get("/api/v1/ping").then().statusCode(200).body("message", equalTo("pong"));
	}

	@Test
	void livenessIsUp() {
		given().when().get("/healthz").then().statusCode(200).body("status", equalTo("up"));
	}

	@Test
	void readinessChecksTheDatabase() {
		given().when().get("/readyz").then().statusCode(200).body("status", equalTo("ready"));
	}

	@Test
	void rootPageIsServed() {
		given().when().get("/").then().statusCode(200).body(containsString("hello"));
	}

	@Test
	void errorResponsesDoNotLeakDetails() {
		given().when().get("/does-not-exist").then().statusCode(404).body(org.hamcrest.Matchers.not(containsString("Exception")));
	}

}
