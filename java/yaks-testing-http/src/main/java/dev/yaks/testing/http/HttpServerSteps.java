package dev.yaks.testing.http;

import java.util.HashMap;
import java.util.Map;

import com.consol.citrus.Citrus;
import com.consol.citrus.annotations.CitrusFramework;
import com.consol.citrus.annotations.CitrusResource;
import com.consol.citrus.dsl.builder.BuilderSupport;
import com.consol.citrus.dsl.builder.HttpActionBuilder;
import com.consol.citrus.dsl.builder.HttpServerActionBuilder;
import com.consol.citrus.dsl.builder.HttpServerRequestActionBuilder;
import com.consol.citrus.dsl.endpoint.CitrusEndpoints;
import com.consol.citrus.dsl.runner.TestRunner;
import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.http.message.HttpMessage;
import com.consol.citrus.http.server.HttpServer;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.cucumber.datatable.DataTable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * @author Christoph Deppisch
 */
public class HttpServerSteps {

    @CitrusResource
    private TestRunner runner;

    @CitrusFramework
    private Citrus citrus;

    private HttpServer httpServer;

    private HttpMessage request;
    private HttpMessage response;

    private Map<String, String> requestHeaders = new HashMap<>();
    private Map<String, String> responseHeaders = new HashMap<>();

    private Map<String, String> bodyValidationExpressions = new HashMap<>();

    private String requestBody;
    private String responseBody;

    @Before
    public void before(Scenario scenario) {
        if (httpServer == null && citrus.getApplicationContext().getBeansOfType(HttpServer.class).size() == 1L) {
            httpServer = citrus.getApplicationContext().getBean(HttpServer.class);
        } else {
            httpServer = CitrusEndpoints.http()
                                        .server()
                                        .build();
        }

        requestHeaders = new HashMap<>();
        responseHeaders = new HashMap<>();
        request = new HttpMessage();
        response = new HttpMessage();
        requestBody = null;
        responseBody = null;
        bodyValidationExpressions = new HashMap<>();
    }

    @Given("^http-server \"([^\"\\s]+)\"$")
    public void setServer(String id) {
        if (!citrus.getApplicationContext().containsBean(id)) {
            throw new CitrusRuntimeException("Unable to find http server for id: " + id);
        }

        httpServer = citrus.getApplicationContext().getBean(id, HttpServer.class);
    }

    @Then("^(?:expect|verify) HTTP request header: ([^\\s]+)(?:=| is )\"(.+)\"$")
    public void addRequestHeader(String name, String value) {
        requestHeaders.put(name, value);
    }

    @Then("^(?:expect|verify) HTTP request headers$")
    public void addRequestHeaders(DataTable headers) {
        Map<String, String> headerPairs = headers.asMap(String.class, String.class);
        headerPairs.forEach(this::addRequestHeader);
    }

    @Given("^HTTP response header: ([^\\s]+)(?:=| is )\"(.+)\"$")
    public void addResponseHeader(String name, String value) {
        responseHeaders.put(name, value);
    }

    @Given("^HTTP response headers$")
    public void addResponseHeaders(DataTable headers) {
        Map<String, String> headerPairs = headers.asMap(String.class, String.class);
        headerPairs.forEach(this::addResponseHeader);
    }

    @Then("^(?:expect|verify) HTTP request expression: ([^\\s]+)(?:=| is )\"(.+)\"$")
    public void addBodyValidationExpression(String name, String value) {
        bodyValidationExpressions.put(name, value);
    }

    @Then("^(?:expect|verify) HTTP request expressions$")
    public void addBodyValidationExpressions(DataTable validationExpressions) {
        Map<String, String> expressions = validationExpressions.asMap(String.class, String.class);
        expressions.forEach(this::addBodyValidationExpression);
    }

    @Given("^HTTP response body$")
    public void setResponseBodyMultiline(String body) {
        setResponseBody(body);
    }

    @Given("^HTTP response body: (.+)$")
    public void setResponseBody(String body) {
        this.responseBody = body;
    }

    @Then("^(?:expect|verify) HTTP request body$")
    public void setRequestBodyMultiline(String body) {
        setRequestBody(body);
    }

    @Then("^(?:expect|verify) HTTP request body: (.+)$")
    public void setRequestBody(String body) {
        this.requestBody = body;
    }

    @When("^receive HTTP request$")
    public void receiveServerRequestFull(String requestData) {
        receiveServerRequest(HttpMessage.fromRequestData(requestData));
    }

    @Then("^send HTTP response$")
    public void sendServerResponseFull(String responseData) {
        sendServerResponse(HttpMessage.fromResponseData(responseData));
    }

    @When("^receive (GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS|TRACE)$")
    public void receiveServerRequestMultilineBody(String method) {
        receiveServerRequest(method, null);
    }

    @When("^receive (GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS|TRACE) ([^\"\\s]+)$")
    public void receiveServerRequest(String method, String path) {
        request.method(HttpMethod.valueOf(method));

        if (StringUtils.hasText(path)) {
            request.path(path);
            request.contextPath(path);
        }

        if (StringUtils.hasText(requestBody)) {
            request.setPayload(requestBody);
        }

        for (Map.Entry<String, String> headerEntry : requestHeaders.entrySet()) {
            request.setHeader(headerEntry.getKey(), headerEntry.getValue());
        }

        receiveServerRequest(request);

        requestBody = null;
        requestHeaders.clear();
    }

    @Then("^send HTTP (\\d+)(?: [^\\s]+)?$")
    public void sendServerResponse(Integer status) {
        response.status(HttpStatus.valueOf(status));

        if (StringUtils.hasText(responseBody)) {
            response.setPayload(responseBody);
        }

        for (Map.Entry<String, String> headerEntry : responseHeaders.entrySet()) {
            response.setHeader(headerEntry.getKey(), headerEntry.getValue());
        }

        sendServerResponse(response);

        responseBody = null;
        responseHeaders.clear();
    }

    /**
     * Receives server request.
     * @param request
     */
    private void receiveServerRequest(HttpMessage request) {
        BuilderSupport<HttpActionBuilder> action = builder -> {
            HttpServerActionBuilder.HttpServerReceiveActionBuilder receiveBuilder = builder.server(httpServer).receive();
            HttpServerRequestActionBuilder requestBuilder;

            if (request.getRequestMethod() == null || request.getRequestMethod().equals(HttpMethod.POST)) {
                requestBuilder = receiveBuilder.post().message(request);
            } else if (request.getRequestMethod().equals(HttpMethod.GET)) {
                requestBuilder = receiveBuilder.get().message(request);
            } else if (request.getRequestMethod().equals(HttpMethod.PUT)) {
                requestBuilder = receiveBuilder.put().message(request);
            } else if (request.getRequestMethod().equals(HttpMethod.DELETE)) {
                requestBuilder = receiveBuilder.delete().message(request);
            } else if (request.getRequestMethod().equals(HttpMethod.HEAD)) {
                requestBuilder = receiveBuilder.head().message(request);
            } else if (request.getRequestMethod().equals(HttpMethod.TRACE)) {
                requestBuilder = receiveBuilder.trace().message(request);
            } else if (request.getRequestMethod().equals(HttpMethod.PATCH)) {
                requestBuilder = receiveBuilder.patch().message(request);
            } else if (request.getRequestMethod().equals(HttpMethod.OPTIONS)) {
                requestBuilder = receiveBuilder.options().message(request);
            } else {
                requestBuilder = receiveBuilder.post().message(request);
            }

            for (Map.Entry<String, String> headerEntry : bodyValidationExpressions.entrySet()) {
                requestBuilder.validate(headerEntry.getKey(), headerEntry.getValue());
            }
            bodyValidationExpressions.clear();
        };

        runner.http(action);
    }

    /**
     * Sends server response.
     * @param response
     */
    private void sendServerResponse(HttpMessage response) {
        runner.http(action -> action.server(httpServer).send()
                .response(response.getStatusCode())
                .message(response));
    }

}
