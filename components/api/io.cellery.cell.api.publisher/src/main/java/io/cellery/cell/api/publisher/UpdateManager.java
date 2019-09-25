/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.cellery.cell.api.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cellery.cell.api.publisher.beans.controller.API;
import io.cellery.cell.api.publisher.beans.controller.APIMConfig;
import io.cellery.cell.api.publisher.beans.controller.ApiDefinition;
import io.cellery.cell.api.publisher.beans.controller.Cell;
import io.cellery.cell.api.publisher.beans.controller.RestConfig;
import io.cellery.cell.api.publisher.beans.request.ApiCreateRequest;
import io.cellery.cell.api.publisher.beans.request.Endpoint;
import io.cellery.cell.api.publisher.beans.request.InfoDefenietion;
import io.cellery.cell.api.publisher.beans.request.Method;
import io.cellery.cell.api.publisher.beans.request.Parameter;
import io.cellery.cell.api.publisher.beans.request.PathDefinition;
import io.cellery.cell.api.publisher.beans.request.PathsMapping;
import io.cellery.cell.api.publisher.beans.request.ProductionEndpoint;
import io.cellery.cell.api.publisher.exceptions.APIException;
import io.cellery.cell.api.publisher.internals.ConfigManager;
import io.cellery.cell.api.publisher.utils.Constants;
import io.cellery.cell.api.publisher.utils.RequestProcessor;
import io.swagger.models.Info;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.util.Json;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Class for create APIs in global API Manager.
 */
public class UpdateManager {

    private static final Logger log = LoggerFactory.getLogger(UpdateManager.class);
    private static Cell cellConfig;
    private static RestConfig restConfig;
    private static APIMConfig apimConfig;

    public static void main(String[] args) {
        try {
            // Encode username password to base64
            restConfig = ConfigManager.getRestConfiguration();
            cellConfig = ConfigManager.getCellConfiguration();

            List apiIds = createGlobalAPIs();
            publishGlobalAPIs(apiIds);
            generateApiConfigJson();
            log.info("Global API creation is completed successfully..");
        } catch (APIException e) {
            log.error("Error occurred while creating APIs in Global API manager. " + e.getMessage(), e);
            System.exit(Constants.Utils.ERROR_EXIT_CODE);
        }
    }

    /**
     * Create Global APIs.
     *
     * @return created API Ids.
     * @throws APIException throw API Exception if an error occurred while creating APIs.
     */
    private static List createGlobalAPIs() throws APIException {
        if (log.isDebugEnabled()) {
            log.debug("Creating APIs in Global API Manager...");
        }

        JSONArray apiPayloads = createGlobalApiPayloads();
        List<String> apiIDs = new ArrayList<>();
        for (int i = 0; i < apiPayloads.length(); i++) {
            RequestProcessor requestProcessor = new RequestProcessor();
            ObjectMapper objectMapper = new ObjectMapper();
            String apiCreateResponse;
            String createAPIPath;
            try {
                apimConfig = ConfigManager.getAPIMConfiguration();
                createAPIPath = restConfig.getApimBaseUrl() + Constants.Utils.PATH_PUBLISHER
                        + restConfig.getApiVersion() + Constants.Utils.PATH_APIS;
                apiCreateResponse = requestProcessor
                        .doPost(createAPIPath, Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
                                Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
                                Constants.Utils.BEARER + apimConfig.getApiToken(),
                                objectMapper.writeValueAsString(apiPayloads.get(i)));
            } catch (JsonProcessingException e) {
                throw new APIException("Error while serializing the payload: " + apiPayloads.get(i));
            }

            if (apiCreateResponse != null) {
                if (!(apiCreateResponse.contains(Constants.Utils.DUPLICATE_API_ERROR) ||
                        apiCreateResponse.contains(Constants.Utils.DIFFERENT_CONTEXT_ERROR) ||
                        apiCreateResponse.contains(Constants.Utils.DUPLICATE_CONTEXT_ERROR))) {
                    JSONObject jsonObj = new JSONObject(apiCreateResponse);
                    apiIDs.add(jsonObj.getString(Constants.Utils.ID));
                }
            } else {
                throw new APIException("Error while creating the global API from: " + createAPIPath);
            }
        }
        return apiIDs;
    }

    /**
     * Create Global API Payloads
     *
     * @return JSONArray that contains global API Payloads
     * @throws APIException throw API Exception if an error occurred while creating APIs.
     */
    private static JSONArray createGlobalApiPayloads() throws APIException {
        JSONArray apiPayloadsArray = new JSONArray();
        List<API> apis = cellConfig.getApis();

        for (API api : apis) {
            if (api.isGlobal()) {
                // Create api payload with gateway backend
                String existingApiId = requireCreateAsNewApi(api);
                if (existingApiId.equals("")) {
                    ApiCreateRequest globalApiCreateRequest = new ApiCreateRequest();
                    globalApiCreateRequest.setName(generateAPIName(api));
                    globalApiCreateRequest.setContext(getContext(api));
                    globalApiCreateRequest.setVersion(getVersion(api));
                    globalApiCreateRequest.setApiDefinition(getAPIDefinition(api));
                    globalApiCreateRequest.setEndpointConfig(getGlobalEndpoint(api));
                    globalApiCreateRequest.setGatewayEnvironments(Constants.Utils.PRODUCTION_AND_SANDBOX);

                    // Set some additional properties.
                    Map<String, String> additionalProperties = new HashMap<>();
                    additionalProperties.put(Constants.Utils.CELL_NAME_PROPERTY, cellConfig.getCell());
                    additionalProperties.put(Constants.Utils.API_CONTEXT_PROPERTY, getContext(api));
                    additionalProperties.put(Constants.Utils.CELLNAME_N_CONTEXT_PROPERTY, cellConfig.getCell() +
                            getContext(api).replace("/", "_"));
                    globalApiCreateRequest.setAdditionalProperties(additionalProperties);

                    apiPayloadsArray.put(globalApiCreateRequest);
                } else {
                    if (requireCreateAsNewVersion(api)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Need to publish as a new api");
                        }
                        String newApiVersionId = createNewApiVersion(existingApiId, "4.9.94");
                        if (log.isDebugEnabled()) {
                            log.debug("Newly created api version Id : " + newApiVersionId);
                        }
                    }
                }
            }
        }
        return apiPayloadsArray;
    }

    /**
     * Publish APIs in created state.
     *
     * @param ids API Id list
     * @throws APIException Throw API Exception if an error occurred while publishing APIs.
     */
    private static void publishGlobalAPIs(List ids) throws APIException {
//        log.info(ids.toString());
        if (log.isDebugEnabled()) {
            log.debug("Publishing created APIs in Global API Manager...");
        }

        for (Object id : ids) {
            log.debug("Id of publishing API :" + id);
            RequestProcessor requestProcessor = new RequestProcessor();
            String apiPublishResponse;
            String apiPublishPath = restConfig.getApimBaseUrl() + Constants.Utils.PATH_PUBLISHER
                    + restConfig.getApiVersion() + Constants.Utils.PATH_LIFECYCLE + "apiId=" + id + "&action=Publish";
            apiPublishResponse = requestProcessor.doPost(apiPublishPath, Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
                    Constants.Utils.CONTENT_TYPE_APPLICATION_JSON, Constants.Utils.BEARER + apimConfig.getApiToken(),
                    Constants.Utils.EMPTY_STRING);

            log.debug("API publish response :" + apiPublishResponse);


            if (apiPublishResponse == null) {
                throw new APIException(
                        "Error while publishing the global API with URL: " + apiPublishPath);
            }
        }
    }

    /**
     * Create endpoint_config payload required for global API creation payload
     *
     * @return endpoint payload string
     */
    private static String getGlobalEndpoint(API api) {
        String response = Constants.Utils.EMPTY_STRING;
        ProductionEndpoint productionEndpoint = new ProductionEndpoint();
        productionEndpoint.setUrl(Constants.Utils.HTTP + (cellConfig.getHostname() + "/" + api.getContext())
                .replaceAll("//", "/"));

        Endpoint endpoint = new Endpoint();
        endpoint.setProductionEndPoint(productionEndpoint);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            response = objectMapper.writeValueAsString(endpoint);
        } catch (JsonProcessingException e) {
            log.error("Error occurred while serializing json to string", e);
        }
        return response;
    }

    /**
     * Create api definition payload required for API creation payload
     *
     * @param api Api details
     * @return api definition payload string
     */
    private static String getAPIDefinition(API api) throws APIException {
        PathsMapping apiDefinition = new PathsMapping();
        List<ApiDefinition> definitions = api.getDefinitions();

        for (ApiDefinition definition : definitions) {
            PathDefinition pathDefinition;
            Method method = new Method();
            if (!api.isAuthenticate()) {
                // Set authenticate none for the resource
                method.setxAuthType(Constants.JsonParamNames.NONE);
            }
            String methodStr = definition.getMethod();
            String path = definition.getPath().replaceAll("/$", Constants.Utils.EMPTY_STRING);

            String allowQueryPath;
            if (path.endsWith(Constants.Utils.WILDCARD_PATTERN)) {
                // Avoid adding query pattern if path is a wildcard
                allowQueryPath = Constants.Utils.WILDCARD_PATTERN;
            } else {
                // Append /* to allow query parameters and path parameters
                allowQueryPath = path + Constants.Utils.ALLOW_QUERY_PATTERN;
            }

            // If already contain a key, update path definition.
            if (apiDefinition.getPaths().containsKey(allowQueryPath)) {
                pathDefinition = apiDefinition.getPaths().get(allowQueryPath);
            } else {
                pathDefinition = new PathDefinition();
            }
            switch (methodStr) {
                case "GET":
                    pathDefinition.setGet(method);
                    break;
                case "POST":
                    Parameter parameter = new Parameter();
                    parameter.setName(Constants.Utils.BODY);
                    parameter.setIn(Constants.Utils.BODY);
                    method.setParameters(Collections.singletonList(parameter));
                    pathDefinition.setPost(method);
                    break;
                default:
                    throw new APIException("Method: " + methodStr + " is not implemented");
            }
            apiDefinition.addPathDefinition(allowQueryPath, pathDefinition);
        }
        InfoDefenietion info = new InfoDefenietion();
        info.setTitle(generateAPIName(api));
        info.setVersion(getVersion(api));
        apiDefinition.setInfo(info);
        ObjectMapper objectMapper = new ObjectMapper();
        String apiDefinitionStr = Constants.Utils.EMPTY_STRING;

        try {
            apiDefinitionStr = objectMapper.writeValueAsString(apiDefinition);
        } catch (JsonProcessingException e) {
            log.error("Error occurred while serializing json to string", e);
        }
        return apiDefinitionStr;
    }

    /**
     * Gets API Config file from the disk
     *
     * @return Apiconfig JSONArray
     */
    private static JSONArray getApiConfig() {
        JSONArray apiConfigArray = null;
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get(Constants.Utils.API_CONFIG_PATH)),
                    StandardCharsets.UTF_8);
            apiConfigArray = new JSONArray(jsonContent);
        } catch (NoSuchFileException e) {
            createEmptyJSONArray();
            apiConfigArray = getApiConfig();
        } catch (IOException e) {
            log.error("Failed to read API config", e);
        }
        return apiConfigArray;
    }

    /**
     * Writes to a File
     *
     * @param path    path of the file
     * @param content Content that should be written
     */
    private static void writeToAFile(String path, String content) {
        try {
            java.nio.file.Path pathToFile = Paths.get(path);

            java.nio.file.Path parent = pathToFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.write(pathToFile, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
        } catch (IOException e) {
            log.error("Failed to write to file " + path, e);
        }
    }

    /**
     * Creates an empty JSON Array and writes to the API Config File
     */
    private static void createEmptyJSONArray() {
        JSONArray emptyArr = new JSONArray();
        writeToAFile(Constants.Utils.API_CONFIG_PATH, emptyArr.toString());
    }

    /**
     * Generates API Config that is required by Micro-GW
     */
    private static void generateApiConfigJson() {
        List<API> apis = cellConfig.getApis();
        for (API api : apis) {
            createSwagger(api);
//            writeToMicroGWConfig(api);
        }
    }

    /**
     * Creates Swagger file for the API given by controller
     *
     * @param api API sent by controller
     */
    private static void createSwagger(API api) {
        Swagger swagger = new Swagger();
        swagger.setInfo(createSwaggerInfo(api));
        swagger.basePath("/" + api.getContext());
        swagger.setPaths(createAPIResources(api));
        String swaggerString = Json.pretty(swagger);
        writeSwagger(swaggerString, removeSpecialChars(api.getDestination().getHost() +
                api.getDestination().getPort() + api.getContext()));
    }

    /**
     * Creates Swagger Info
     *
     * @param api API sent by controller
     * @return Swagger Info
     */
    private static Info createSwaggerInfo(API api) {
        Info info = new Info();
        info.setVersion(cellConfig.getVersion());
        info.setTitle(generateAPIName(api));
        return info;
    }

    /**
     * Creates API Resources inside a Component
     *
     * @param api API sent by controller
     * @return Swagger Path Map
     */
    private static Map<String, Path> createAPIResources(API api) {
        Map<String, Path> pathMap = new HashMap<>();
        for (ApiDefinition definition : api.getDefinitions()) {
            Path path = pathMap.computeIfAbsent(definition.getPath(), (key) -> new Path());
            Operation op = new Operation();

            Map<String, Response> resMap = new HashMap<>();
            Response res = new Response();
            res.setDescription("Successful");

            resMap.put("200", res);
            op.setResponses(resMap);

            disableMicroGWAuth(op);
            switch (definition.getMethod().toLowerCase(Locale.ENGLISH)) {
                case Constants.JsonParamNames.GET:
                    path.setGet(op);
                    break;
                case Constants.JsonParamNames.POST:
                    path.setPost(op);
                    break;
                case Constants.JsonParamNames.PUT:
                    path.setPut(op);
                    break;
                case Constants.JsonParamNames.DELETE:
                    path.setDelete(op);
                    break;
                default:
                    log.error("HTTP Method not implemented");
            }
        }
        return pathMap;
    }

    /**
     * Adds X-auth to swagger to disable Micro-GW authentication
     *
     * @param operation Swagger operation
     */
    private static void disableMicroGWAuth(Operation operation) {
        operation.setVendorExtension(Constants.JsonParamNames.X_AUTH_TYPE, Constants.JsonParamNames.NONE);
    }

    /**
     * Generates API name for global API
     *
     * @param api API sent by controller
     * @return API name
     */
    private static String generateAPIName(API api) {
//        String apiName1 = "";
//        if (cellConfig.getGlobalContext().equals(Constants.Utils.EMPTY_STRING)) {
//            apiName1 = cellConfig.getCell() + Constants.Utils.UNDERSCORE + api.getContext();
//        } else {
//            apiName1 = cellConfig.getCell() + Constants.Utils.UNDERSCORE + cellConfig.getGlobalContext() +
//                    Constants.Utils.UNDERSCORE + api.getContext();
//        }
        String apiName = cellConfig.getCell() + Constants.Utils.UNDERSCORE + Constants.Utils.GLOBAL +
                Constants.Utils.UNDERSCORE + cellConfig.getVersion() + Constants.Utils.UNDERSCORE +
                api.getContext().replace("/", Constants.Utils.EMPTY_STRING);
        return apiName.replaceAll("[^a-zA-Z0-9]", "_");
    }

    /**
     * Writes Swagger String to a File
     *
     * @param swagger Swagger String
     * @param name    Path of the destination file
     */
    private static void writeSwagger(String swagger, String name) {
        String path = Constants.Utils.SWAGGER_FOLDER + name + ".json";
        writeToAFile(path, swagger);
    }

    /**
     * Removes special characters from a given string
     *
     * @param string String with special characters
     * @return String without special Characters
     */
    private static String removeSpecialChars(String string) {
        return string.replaceAll("[^a-zA-Z0-9]", "");
    }

    private static String getContext(API api) {
        if (cellConfig.getGlobalContext().equals(Constants.Utils.EMPTY_STRING)) {
            return (cellConfig.getCell() + "/" + api.getContext()).replaceAll("//", "/");
        } else {
            return (cellConfig.getGlobalContext() + "/" + api.getContext())
                    .replaceAll("//", "/");
        }
    }

    private static String getVersion(API api) {
        if (cellConfig.getVersion().equals(Constants.Utils.EMPTY_STRING)) {
            return api.getVersion();
        } else {
            return cellConfig.getVersion();
        }
    }

    /**
     * Check the need of publishing a new Global API.
     *
     * @return true if new api need to be published.
     * @throws APIException throw API Exception if an error occurred while checking APIs availability.
     */
    private static String requireCreateAsNewApi(API api) throws APIException {
        if (log.isDebugEnabled()) {
            log.debug("Checking APIs need to be published as a new api in Global API Manager...");
        }

        RequestProcessor requestProcessor = new RequestProcessor();
        String apiRetrieveResponse;
        String apiRetrievePath;

        apimConfig = ConfigManager.getAPIMConfiguration();
        apiRetrievePath = restConfig.getApimBaseUrl() + Constants.Utils.PATH_PUBLISHER
                + restConfig.getApiVersion() + Constants.Utils.PATH_APIS + "?query=cellncontext:hr_gl_newinfo";
//        Map<String, String> queryParams = new HashMap<>();
//        queryParams.put(Constants.Utils.CELL_NAME_PROPERTY, cellConfig.getCell());
//        queryParams.put(Constants.Utils.API_CONTEXT_PROPERTY, getContext(api));
//        queryParams.put(Constants.Utils.API_CONTEXT_PROPERTY, getContext(api));
        apiRetrieveResponse = requestProcessor
                .doGet(apiRetrievePath, Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
                        Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
                        Constants.Utils.BEARER + apimConfig.getApiToken());

        if (apiRetrieveResponse != null) {
            JSONObject jsonObj = new JSONObject(apiRetrieveResponse);
            int apiCount = jsonObj.getInt(Constants.Utils.COUNT);
            if (log.isDebugEnabled()) {
                log.debug("New Api Check Api Count :" + apiCount);
                log.debug("New Api Check payload :" + jsonObj);
            }
            if (apiCount > 0) {
                String id = jsonObj.getJSONArray("list").getJSONObject(0).getString("id");
                if (log.isDebugEnabled()) {
                    log.debug("New Api Check Api ID :" + id);
                }
                return id;
            }
//            return apiCount == 0;
        } else {
            throw new APIException("Error while retrieving apis from the global API");
        }
        return "";
    }

    /**
     * Check the need of publishing a new Global API version.
     *
     * @return true if api need to be published as a new version.
     * @throws APIException throw API Exception if an error occurred while checking APIs availability.
     */
    private static Boolean requireCreateAsNewVersion(API api) throws APIException {
        if (log.isDebugEnabled()) {
            log.debug("Checking APIs need to be published as a new version in Global API Manager...");
        }

        RequestProcessor requestProcessor = new RequestProcessor();
        String apiRetrieveResponse;
        String apiRetrievePath;

        apimConfig = ConfigManager.getAPIMConfiguration();
        apiRetrievePath = restConfig.getApimBaseUrl() + Constants.Utils.PATH_PUBLISHER
                + restConfig.getApiVersion() + Constants.Utils.PATH_APIS + "?query=cellncontext:hr_gl_newinfo%20version:4.9.94";
//        Map<String, String> queryParams = new HashMap<>();
//        queryParams.put(Constants.Utils.CELL_NAME_PROPERTY, cellConfig.getCell());
//        queryParams.put(Constants.Utils.API_CONTEXT_PROPERTY, getContext(api));
//        queryParams.put(Constants.JsonParamNames.VERSION, getVersion(api));
        apiRetrieveResponse = requestProcessor
                .doGet(apiRetrievePath, Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
                        Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
                        Constants.Utils.BEARER + apimConfig.getApiToken());

        if (apiRetrieveResponse != null) {
            JSONObject jsonObj = new JSONObject(apiRetrieveResponse);
            int apiCount = jsonObj.getInt(Constants.Utils.COUNT);
            if (log.isDebugEnabled()) {
                log.debug("Api versioning Check Api Count :" + apiCount);
                log.debug("Api versioning Check payload :" + jsonObj.toString());
            }
            return apiCount == 0;
        } else {
            throw new APIException("Error while retrieving apis from the global API");
        }
    }

    /**
     * Create new Global API version
     *
     * @return String that contains created API ID
     * @throws APIException throw API Exception if an error occurred while creating new version of API.
     */
    private static String createNewApiVersion(String existingApiId, String version) throws APIException {
        String newApiVersionId = "";
        if (log.isDebugEnabled()) {
            log.debug("Creating new api version for existing Api. ID:" + existingApiId);
        }
        RequestProcessor requestProcessor = new RequestProcessor();
        String createApiVersionResponse;
        String createApiVersionPath = restConfig.getApimBaseUrl() + Constants.Utils.PATH_PUBLISHER
                + restConfig.getApiVersion() + Constants.Utils.PATH_CREATE_NEW_VERSION + "apiId=" + existingApiId + "&newVersion=" + version;
        createApiVersionResponse = requestProcessor.doPost(createApiVersionPath, Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
                Constants.Utils.CONTENT_TYPE_APPLICATION_JSON, Constants.Utils.BEARER + apimConfig.getApiToken(),
                Constants.Utils.EMPTY_STRING);

        log.debug("Create new API version response :" + createApiVersionResponse);

        if (createApiVersionResponse != null) {
            JSONObject jsonObj = new JSONObject(createApiVersionResponse);
            return jsonObj.getString(Constants.Utils.ID);
        } else {
            throw new APIException("Error while creating the new API version with URL: " + createApiVersionPath);
        }
    }
}
