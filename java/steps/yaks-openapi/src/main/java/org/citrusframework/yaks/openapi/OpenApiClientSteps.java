/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citrusframework.yaks.openapi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.consol.citrus.Citrus;
import com.consol.citrus.CitrusSettings;
import com.consol.citrus.TestCaseRunner;
import com.consol.citrus.annotations.CitrusAnnotations;
import com.consol.citrus.annotations.CitrusFramework;
import com.consol.citrus.annotations.CitrusResource;
import com.consol.citrus.context.TestContext;
import io.apicurio.datamodels.openapi.models.OasOperation;
import io.apicurio.datamodels.openapi.models.OasParameter;
import io.apicurio.datamodels.openapi.models.OasPathItem;
import io.apicurio.datamodels.openapi.models.OasResponse;
import io.apicurio.datamodels.openapi.models.OasSchema;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.citrusframework.yaks.http.HttpClientSteps;
import org.citrusframework.yaks.openapi.model.OasModelHelper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class OpenApiClientSteps {

    @CitrusResource
    private TestCaseRunner runner;

    @CitrusResource
    private TestContext context;

    @CitrusFramework
    private Citrus citrus;

    private HttpClientSteps clientSteps;

    private OasOperation operation;

    private long timeout = OpenApiSettings.getTimeout();

    @Before
    public void before(Scenario scenario) {
        clientSteps = new HttpClientSteps();
        CitrusAnnotations.injectAll(clientSteps, citrus);
        CitrusAnnotations.injectTestRunner(clientSteps, runner);
        clientSteps.before(scenario);

        clientSteps.configureTimeout(timeout);

        operation = null;
    }

    @Given("^OpenAPI request timeout is (\\d+)(?: ms| milliseconds)$")
    public void configureTimeout(long timeout) {
        this.timeout = timeout;
        clientSteps.configureTimeout(timeout);
    }

    @Given("^OpenAPI request fork mode is (enabled|disabled)$")
    public void configureForkMode(String mode) {
        clientSteps.configureForkMode(mode);
    }

    @When("^(?:send|invoke) operation: (.+)$")
    public void invokeOperation(String operationId) {
        for (OasPathItem path : OasModelHelper.getPathItems(OpenApiSteps.openApiDoc.paths)) {
            Optional<Map.Entry<String, OasOperation>> operationEntry = OasModelHelper.getOperationMap(path).entrySet().stream()
                    .filter(op -> operationId.equals(op.getValue().operationId))
                    .findFirst();

            if (operationEntry.isPresent()) {
                operation = operationEntry.get().getValue();
                sendRequest(path.getPath(), operationEntry.get().getKey(), operationEntry.get().getValue());
                break;
            }
        }
    }

    @Then("^(?:expect|verify) operation result: (\\d+)(?: [^\\s]+)?$")
    public void verifyResponseByStatus(int response) {
        receiveResponse(operation, String.valueOf(response));
    }

    @And("^(?:expect|verify) operation response: (.+)$")
    public void verifyResponseByName(String response) {
        receiveResponse(operation, response);
    }

    /**
     * Invoke request for given API operation. The request parameters, headers and payload are generated via specification
     * details in that operation.
     * @param path
     * @param method
     * @param operation
     */
    private void sendRequest(String path, String method, OasOperation operation) {
        if (operation.parameters != null) {
            operation.parameters.stream()
                    .filter(param -> "header".equals(param.in))
                    .filter(param -> param.required != null && param.required)
                    .forEach(param -> clientSteps.addRequestHeader(param.getName(), OpenApiTestDataGenerator.createRandomValueExpression((OasSchema) param.schema, OasModelHelper.getSchemaDefinitions(OpenApiSteps.openApiDoc), false)));

            operation.parameters.stream()
                    .filter(param -> "query".equals(param.in))
                    .filter(param -> param.required != null && param.required)
                    .forEach(param -> clientSteps.addRequestQueryParam(param.getName(), OpenApiTestDataGenerator.createRandomValueExpression((OasSchema) param.schema)));
        }

        Optional<OasSchema> body = OasModelHelper.getRequestBodySchema(OpenApiSteps.openApiDoc, operation);
        if (body.isPresent()) {
            clientSteps.setRequestBody(OpenApiTestDataGenerator.createOutboundPayload(body.get(), OasModelHelper.getSchemaDefinitions(OpenApiSteps.openApiDoc)));

            if (OasModelHelper.isReferenceType(body.get())
                    || OasModelHelper.isObjectType(body.get())
                    || OasModelHelper.isArrayType(body.get())) {
                clientSteps.setOutboundDictionary(OpenApiSteps.outboundDictionary);
            }
        }

        String randomizedPath = path;
        if (operation.parameters != null) {
            List<OasParameter> pathParams = operation.parameters.stream()
                    .filter(p -> "path".equals(p.in))
                    .collect(Collectors.toList());

            for (OasParameter parameter : pathParams) {
                String parameterValue;
                if (context.getVariables().containsKey(parameter.getName())) {
                    parameterValue = "\\" + CitrusSettings.VARIABLE_PREFIX + parameter.getName() + CitrusSettings.VARIABLE_SUFFIX;
                } else {
                    parameterValue = OpenApiTestDataGenerator.createRandomValueExpression((OasSchema) parameter.schema);
                }
                randomizedPath = Pattern.compile("\\{" + parameter.getName() + "}")
                                        .matcher(randomizedPath)
                                        .replaceAll(parameterValue);
            }
        }

        Optional<String> contentType = OasModelHelper.getRequestContentType(operation);
        contentType.ifPresent(s -> clientSteps.addRequestHeader(HttpHeaders.CONTENT_TYPE, s));

        clientSteps.setUrl(OpenApiSteps.openApiUrl);
        clientSteps.sendClientRequest(method.toUpperCase(), randomizedPath);
    }

    /**
     * Verify operation response where expected parameters, headers and payload are generated using the operation specification details.
     * @param operation
     * @param status
     */
    private void receiveResponse(OasOperation operation, String status) {
        if (operation.responses != null) {
            OasResponse response = Optional.ofNullable(operation.responses.getItem(status))
                                        .orElse(operation.responses.default_);

            if (response != null) {
                Map<String, OasSchema> headers = OasModelHelper.getRequiredHeaders(response);
                if (headers != null) {
                    for (Map.Entry<String, OasSchema> header : headers.entrySet()) {
                        clientSteps.addResponseHeader(header.getKey(), OpenApiTestDataGenerator.createValidationExpression(header.getValue(), OasModelHelper.getSchemaDefinitions(OpenApiSteps.openApiDoc), false));
                    }
                }

                Optional<OasSchema> responseSchema = OasModelHelper.getSchema(response);
                if (responseSchema.isPresent()) {
                    clientSteps.setResponseBody(OpenApiTestDataGenerator.createInboundPayload(responseSchema.get(), OasModelHelper.getSchemaDefinitions(OpenApiSteps.openApiDoc)));

                    if (OasModelHelper.isReferenceType(responseSchema.get())
                            || OasModelHelper.isObjectType(responseSchema.get())
                            || OasModelHelper.isArrayType(responseSchema.get())) {
                        clientSteps.setInboundDictionary(OpenApiSteps.inboundDictionary);
                    }
                }
            }
        }

        Optional<String> contentType = OasModelHelper.getResponseContentType(OpenApiSteps.openApiDoc, operation);
        contentType.ifPresent(s -> clientSteps.addResponseHeader(HttpHeaders.CONTENT_TYPE, s));

        if (Pattern.compile("[0-9]+").matcher(status).matches()) {
            clientSteps.receiveClientResponse(Integer.parseInt(status));
        } else {
            clientSteps.receiveClientResponse(HttpStatus.OK.value());
        }
    }
}
