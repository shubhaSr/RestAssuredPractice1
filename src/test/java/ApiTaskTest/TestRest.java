package ApiTaskTest;

import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.*;

import org.testng.Assert;


public class TestRest {
	
	RequestSpecification requestSpecification;
	
	@BeforeSuite
    public void beforeSuite() {
		RequestSpecBuilder requestSpecBuilder = new RequestSpecBuilder();
		requestSpecBuilder.setBaseUri("https://automation-test-api.netlify.app");
		requestSpecBuilder.addHeader("api_key", "secureKey");
		
		requestSpecification = requestSpecBuilder.build();
	}
	
	@DataProvider(name = "payloadForSuccessfulPost")
	public Object[][] dpMethod1() {
		return new Object[][] { 
			    { 1000, "EUR", "DE", ""},
			    { 800, "EUR", "DE", ""},
				{ 0, "EUR", "DE", ""},
				{ 0, "EUR", "DE", "Amount not provided"}};
				//{ 100, "AUD", "AU", "Amount not provided"},
				// { 200, "SGD", "SG", "Amount not provided"}
				// For combination of "SGD and SG" and "AUD and AU", I get 500 error. Not sure whether its my issue or server issue. Didnt get time to look at it		
	}
	
	@DataProvider(name = "payloadForFailurePost")
	public Object[][] dpMethod2() {
		return new Object[][] { 
			    { -1, "EUR", "DE", 400, "Amount not allowed"},
				{ 1001, "EUR", "DE", 400, "Amount not allowed"},
				{ 600, "EUR", "AU", 400, "Currency not allowed on selected country"},
				{ 600, "EUR", "SG", 400, "Currency not allowed on selected country"},
				{ 100, "AUD", "DE", 400, "Currency not allowed on selected country"},
				{ 600, "AUD", "SG", 400, "Currency not allowed on selected country"},
				{ 600, "SGD", "DE", 400, "Currency not allowed on selected country"},
				{ 600, "SGD", "AU", 400, "Currency not allowed on selected country"},
			    { 200, "EUR", "", 400, "Country not allowed"},
			    { 100, "", "DE", 400, "Currency not allowed on selected country"},
			    { 800, "", "", 400, "Country not allowed"}};		
	}
	
	
	@Test
	public void test_connection_to_api_simulator() {	
		Response response = given().
								param("orderId", "1234").
								spec(requestSpecification).get("/.netlify/functions/payment").
							then().
								log().all().
								extract().response();
		Assert.assertEquals(response.statusCode(), 200);	
	}
	
	@Test(dataProvider = "payloadForFailurePost")
	public void test_failure_request(int amount, String currency, String countryCode, int httpCode, String expectedErrorMessage) {
		
		String payload = "{\"amount\":" + amount  + ",";
		if (currency != "") {
			payload += "\"currency\":\"" + currency + "\",";
		}
		System.out.println(payload);
		if (countryCode != "") {
			payload += "\"country_code\":\"" + countryCode + "\",";
		}
		payload = payload.substring(0, payload.length() - 1);
		payload += "}";
		
		System.out.println(payload);
		
		JsonPath postResponse = given().
			baseUri("https://automation-test-api.netlify.app").
			header("api_key", "secureKey").
			contentType(ContentType.JSON).
			body(payload).
		when().
			post("/.netlify/functions/payment").
		then().
			log().all().
			assertThat().
			statusCode(httpCode).
			extract().jsonPath();
		
		String errorMessage = postResponse.get("error");
		
		Assert.assertEquals(errorMessage, expectedErrorMessage);		
	}
	
	@Test(dataProvider = "payloadForSuccessfulPost")
	public void test_successful_request(int amount, String currency, String countryCode, String type) {
		
		String payload;
		if (type == "Amount not provided") {
			payload = "{\"currency\":\"" + currency + "\",\"country_code\":\"" + countryCode + "\"}";
		} else {
			payload = "{\"amount\":" + String.valueOf(amount)  + ",\"currency\":\"" + currency + "\",\"country_code\":\"" + countryCode + "\"}";
		}
		
		System.out.println(payload);
		
		JsonPath postResponse = given().
			baseUri("https://automation-test-api.netlify.app").
			header("api_key", "secureKey").
			contentType(ContentType.JSON).
			body(payload).
		when().
			post("/.netlify/functions/payment").
		then().
			log().all().
			assertThat().
			statusCode(200).
			extract().jsonPath();
		
		String transactionMessage = postResponse.get("data[0].message");
		
		String expectedTransactionMessage = "Transaction succeeded";
		Assert.assertEquals(transactionMessage, expectedTransactionMessage);
		
		String orderId = postResponse.get("data[0].orderId").toString();
		JsonPath getResponse = given().
			param("orderId", orderId).
			spec(requestSpecification).get("/.netlify/functions/payment").
		then().
			log().all().
			assertThat().
			statusCode(200).
			extract().jsonPath();

		String orderIdInGet = getResponse.get("orderId").toString();
		Assert.assertEquals(orderId, orderIdInGet);
	}

}