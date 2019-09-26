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
import io.cellery.cell.api.publisher.beans.request.ApiUpdateRequest;
import io.cellery.cell.api.publisher.beans.request.Endpoint;
import io.cellery.cell.api.publisher.beans.request.InfoDefinition;
import io.cellery.cell.api.publisher.beans.request.Method;
import io.cellery.cell.api.publisher.beans.request.Parameter;
import io.cellery.cell.api.publisher.beans.request.PathDefinition;
import io.cellery.cell.api.publisher.beans.request.PathsMapping;
import io.cellery.cell.api.publisher.beans.request.ProductionEndpoint;
import io.cellery.cell.api.publisher.exceptions.APIException;
import io.cellery.cell.api.publisher.internals.ConfigManager;
import io.cellery.cell.api.publisher.utils.Constants;
import io.cellery.cell.api.publisher.utils.RequestProcessor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Class for create APIs in global API Manager. */
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

      manageApis();
      log.info("Global API publisher completed successfully..");
    } catch (APIException e) {
      log.error("Error occurred while publishing APIs in Global API manager. " + e.getMessage(), e);
      System.exit(Constants.Utils.ERROR_EXIT_CODE);
    }
  }

  /**
   * Creation, Update and publish APIs in Global API Manager
   *
   * @throws APIException throw API Exception if an error occurred while managing the API.
   */
  private static void manageApis() throws APIException {
    if (log.isDebugEnabled()) {
      log.debug("Managing APIs...");
    }
    List<API> apis = cellConfig.getApis();

    for (API api : apis) {
      if (api.isGlobal()) {
        String existingApiId = getExistingApiId(api);

        if (existingApiId.equals(Constants.Utils.EMPTY_STRING)) {
          ApiCreateRequest globalApiPayload = createGlobalApiPayload(api);
          String id = createGlobalApi(globalApiPayload);
          publishGlobalAPI(id);
        } else if (abelToCreateNewVersion(api)) {
          String newApiVersionId = createNewApiVersion(existingApiId, getVersion(api));
          ApiUpdateRequest globalApiUpdatePayload =
              createGlobalApiUpdatePayload(api, newApiVersionId);
          String id = updateGlobalAPI(globalApiUpdatePayload, newApiVersionId);
          publishGlobalAPI(id);
        }
      }
    }
  }

  /**
   * Create Global API Payload
   *
   * @param api API sent by controller
   * @return JSONArray that contains global API Payload
   * @throws APIException throw API Exception if an error occurred while creating the API.
   */
  private static ApiCreateRequest createGlobalApiPayload(API api) throws APIException {
    if (log.isDebugEnabled()) {
      log.debug("Creating Global API payload");
    }
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
    additionalProperties.put(
        Constants.Utils.CELLNAME_N_CONTEXT_PROPERTY,
        cellConfig.getCell() + getContext(api).replace("/", "_"));
    globalApiCreateRequest.setAdditionalProperties(additionalProperties);

    return globalApiCreateRequest;
  }

  /**
   * Create Global API.
   *
   * @param createApiPayload payload of API to be created in Global API manager
   * @return created API Id.
   * @throws APIException throw API Exception if an error occurred while creating the API.
   */
  private static String createGlobalApi(ApiCreateRequest createApiPayload) throws APIException {
    if (log.isDebugEnabled()) {
      log.debug("Creating Global API in Global API Manager");
    }
    RequestProcessor requestProcessor = new RequestProcessor();
    ObjectMapper objectMapper = new ObjectMapper();
    String apiCreateResponse;
    String createAPIPath;
    try {
      apimConfig = ConfigManager.getAPIMConfiguration();
      createAPIPath =
          restConfig.getApimBaseUrl()
              + Constants.Utils.PATH_PUBLISHER
              + restConfig.getApiVersion()
              + Constants.Utils.PATH_APIS;
      apiCreateResponse =
          requestProcessor.doPost(
              createAPIPath,
              Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
              Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
              Constants.Utils.BEARER + apimConfig.getApiToken(),
              objectMapper.writeValueAsString(createApiPayload));
    } catch (JsonProcessingException e) {
      throw new APIException("Error while serializing the payload: " + createApiPayload);
    }

    if (apiCreateResponse != null) {
      if (!(apiCreateResponse.contains(Constants.Utils.DUPLICATE_API_ERROR)
          || apiCreateResponse.contains(Constants.Utils.DIFFERENT_CONTEXT_ERROR)
          || apiCreateResponse.contains(Constants.Utils.DUPLICATE_CONTEXT_ERROR))) {
        JSONObject jsonObj = new JSONObject(apiCreateResponse);
        return jsonObj.getString(Constants.Utils.ID);
      }
    } else {
      throw new APIException("Error while creating the global API from: " + createAPIPath);
    }
    return Constants.Utils.EMPTY_STRING;
  }

  /**
   * Publish API in created state.
   *
   * @param id API Id
   * @throws APIException Throw API Exception if an error occurred while publishing an API.
   */
  private static void publishGlobalAPI(String id) throws APIException {
    if (log.isDebugEnabled()) {
      log.debug("Publishing created API in Global API Manager");
    }

    RequestProcessor requestProcessor = new RequestProcessor();
    String apiPublishResponse;
    String apiPublishPath =
        restConfig.getApimBaseUrl()
            + Constants.Utils.PATH_PUBLISHER
            + restConfig.getApiVersion()
            + Constants.Utils.PATH_LIFECYCLE
            + "apiId="
            + id
            + "&action=Publish";
    apiPublishResponse =
        requestProcessor.doPost(
            apiPublishPath,
            Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
            Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
            Constants.Utils.BEARER + apimConfig.getApiToken(),
            Constants.Utils.EMPTY_STRING);

    if (apiPublishResponse == null) {
      throw new APIException("Error while publishing the global API with URL: " + apiPublishPath);
    }
  }

  /**
   * Create new Global API version
   *
   * @param existingApiId Id of already existing API in Global api manager
   * @param version new version of API
   * @return String that contains created API ID
   * @throws APIException throw API Exception if an error occurred while creating new version of
   *     API.
   */
  private static String createNewApiVersion(String existingApiId, String version)
      throws APIException {
    if (log.isDebugEnabled()) {
      log.debug("Creating new api version for existing API with Id:" + existingApiId);
    }
    RequestProcessor requestProcessor = new RequestProcessor();
    String createApiVersionResponse;
    String createApiVersionPath =
        restConfig.getApimBaseUrl()
            + Constants.Utils.PATH_PUBLISHER
            + restConfig.getApiVersion()
            + Constants.Utils.PATH_CREATE_NEW_VERSION
            + "apiId="
            + existingApiId
            + "&newVersion="
            + version;
    createApiVersionResponse =
        requestProcessor.doPost(
            createApiVersionPath,
            Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
            Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
            Constants.Utils.BEARER + apimConfig.getApiToken(),
            Constants.Utils.EMPTY_STRING);

    if (createApiVersionResponse != null) {
      JSONObject jsonObj = new JSONObject(createApiVersionResponse);
      return jsonObj.getString(Constants.Utils.ID);
    } else {
      throw new APIException(
          "Error while creating the new API version with URL: " + createApiVersionPath);
    }
  }

  /**
   * Create Global API Update Payload
   *
   * @param api API sent by controller
   * @param id Id of API to be updated with new payload
   * @return JSONArray that contains global API update Payload
   * @throws APIException throw API Exception if an error occurred while updating the API.
   */
  private static ApiUpdateRequest createGlobalApiUpdatePayload(API api, String id)
      throws APIException {
    if (log.isDebugEnabled()) {
      log.debug("Creating payload for update Global API");
    }
    ApiUpdateRequest globalApiUpdateRequest = new ApiUpdateRequest();
    globalApiUpdateRequest.setId(id);
    globalApiUpdateRequest.setApiDefinition(getAPIDefinition(api));
    globalApiUpdateRequest.setEndpointConfig(getGlobalEndpoint(api));
    globalApiUpdateRequest.setGatewayEnvironments(Constants.Utils.PRODUCTION_AND_SANDBOX);

    // Set some additional properties.
    Map<String, String> additionalProperties = new HashMap<>();
    additionalProperties.put(Constants.Utils.CELL_NAME_PROPERTY, cellConfig.getCell());
    additionalProperties.put(
        Constants.Utils.CELLNAME_N_CONTEXT_PROPERTY,
        cellConfig.getCell() + getContext(api).replace("/", "_"));
    globalApiUpdateRequest.setAdditionalProperties(additionalProperties);

    return globalApiUpdateRequest;
  }

  /**
   * Update Global API.
   *
   * @param globalApiUpdatePayload API payload to be updated
   * @param id ID of API to be updated
   * @return updated API Id.
   * @throws APIException throw API Exception if an error occurred while updating the API.
   */
  private static String updateGlobalAPI(ApiUpdateRequest globalApiUpdatePayload, String id)
      throws APIException {
    if (log.isDebugEnabled()) {
      log.debug("Updating Global API in Global API manager");
    }
    ObjectMapper objectMapper = new ObjectMapper();
    RequestProcessor requestProcessor = new RequestProcessor();
    String apiUpdateResponse;
    String apiUpdatePath;
    try {
      apimConfig = ConfigManager.getAPIMConfiguration();
      apiUpdatePath =
          restConfig.getApimBaseUrl()
              + Constants.Utils.PATH_PUBLISHER
              + restConfig.getApiVersion()
              + Constants.Utils.PATH_APIS
              + "/"
              + id;
      apiUpdateResponse =
          requestProcessor.doPut(
              apiUpdatePath,
              Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
              Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
              Constants.Utils.BEARER + apimConfig.getApiToken(),
              objectMapper.writeValueAsString(globalApiUpdatePayload));

    } catch (JsonProcessingException e) {
      throw new APIException("Error while serializing the payload: " + globalApiUpdatePayload);
    }

    if (apiUpdateResponse != null) {
      JSONObject jsonObj = new JSONObject(apiUpdateResponse);
      return jsonObj.getString(Constants.Utils.ID);

    } else {
      throw new APIException("Error while updating the global API from: " + apiUpdatePath);
    }
  }

  /**
   * Get Id of API available in the global apim with given given cell name and context
   *
   * @param api API sent by controller
   * @return Id of an available API
   * @throws APIException throw API Exception if an error occurred while checking APIs availability.
   */
  private static String getExistingApiId(API api) throws APIException {
    if (log.isDebugEnabled()) {
      log.debug("Getting Id of an API available in Global API Manager");
    }

    RequestProcessor requestProcessor = new RequestProcessor();
    apimConfig = ConfigManager.getAPIMConfiguration();

    String apiRetrieveQuery =
        Constants.Utils.CELLNAME_N_CONTEXT_PROPERTY
            + ":"
            + (cellConfig.getCell() + getContext(api)).replace("/", "_");
    String apiRetrievePath =
        restConfig.getApimBaseUrl()
            + Constants.Utils.PATH_PUBLISHER
            + restConfig.getApiVersion()
            + Constants.Utils.PATH_QUERY
            + apiRetrieveQuery;
    log.debug("getExistingApiId path :" + apiRetrievePath);
    String apiRetrieveResponse =
        requestProcessor.doGet(
            apiRetrievePath,
            Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
            Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
            Constants.Utils.BEARER + apimConfig.getApiToken());

    if (apiRetrieveResponse != null) {
      JSONObject jsonObj = new JSONObject(apiRetrieveResponse);
      int apiCount = jsonObj.getInt(Constants.Utils.COUNT);
      if (apiCount > 0) {
        return jsonObj.getJSONArray("list").getJSONObject(0).getString("id");
      }
    } else {
      throw new APIException(
          "Error while retrieving apis from the global API with url " + apiRetrievePath);
    }
    return Constants.Utils.EMPTY_STRING;
  }

  /**
   * Check the API can be published as a new version.
   *
   * @param api API sent by controller
   * @return true if api is able to be published as a new version.
   * @throws APIException throw API Exception if an error occurred while checking APIs availability.
   */
  private static Boolean abelToCreateNewVersion(API api) throws APIException {
    if (log.isDebugEnabled()) {
      log.debug("Checking APIs can be published as a new version in Global API Manager...");
    }

    RequestProcessor requestProcessor = new RequestProcessor();

    apimConfig = ConfigManager.getAPIMConfiguration();
    String apiRetrieveQuery =
        Constants.Utils.CELLNAME_N_CONTEXT_PROPERTY
            + ":"
            + (cellConfig.getCell() + getContext(api)).replace("/", "_")
            + " "
            + Constants.JsonParamNames.VERSION
            + ":"
            + getVersion(api);
    String apiRetrievePath;
    try {
      apiRetrievePath =
          restConfig.getApimBaseUrl()
              + Constants.Utils.PATH_PUBLISHER
              + restConfig.getApiVersion()
              + Constants.Utils.PATH_QUERY
              + URLEncoder.encode(apiRetrieveQuery, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      throw new APIException("Error while encoding the query: " + apiRetrieveQuery);
    }
    String apiRetrieveResponse =
        requestProcessor.doGet(
            apiRetrievePath,
            Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
            Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
            Constants.Utils.BEARER + apimConfig.getApiToken());

    if (apiRetrieveResponse != null) {
      JSONObject jsonObj = new JSONObject(apiRetrieveResponse);
      int apiCount = jsonObj.getInt(Constants.Utils.COUNT);
      return apiCount == 0;
    } else {
      throw new APIException(
          "Error while retrieving apis from the global API with url " + apiRetrievePath);
    }
  }

  /**
   * Create endpoint_config payload required for global API creation payload
   *
   * @param api API sent by controller
   * @return endpoint payload string
   */
  private static String getGlobalEndpoint(API api) {
    String response = Constants.Utils.EMPTY_STRING;
    ProductionEndpoint productionEndpoint = new ProductionEndpoint();
    productionEndpoint.setUrl(
        Constants.Utils.HTTP
            + (cellConfig.getHostname() + "/" + api.getContext()).replaceAll("//", "/"));

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
   * @param api API sent by controller
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
    InfoDefinition info = new InfoDefinition();
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
   * Generates API name for global API
   *
   * @param api API sent by controller
   * @return API name
   */
  private static String generateAPIName(API api) {
    String apiName =
        cellConfig.getCell()
            + Constants.Utils.UNDERSCORE
            + Constants.Utils.GLOBAL
            + Constants.Utils.UNDERSCORE
            + cellConfig.getVersion()
            + Constants.Utils.UNDERSCORE
            + api.getContext().replace("/", Constants.Utils.EMPTY_STRING);
    return apiName.replaceAll("[^a-zA-Z0-9]", "_");
  }

  /**
   * Generates API context for global API
   *
   * @param api API sent by controller
   * @return API context
   */
  private static String getContext(API api) {
    if (cellConfig.getGlobalContext().equals(Constants.Utils.EMPTY_STRING)) {
      return (cellConfig.getCell() + "/" + api.getContext()).replaceAll("//", "/");
    } else {
      return (cellConfig.getGlobalContext() + "/" + api.getContext()).replaceAll("//", "/");
    }
  }

  /**
   * Generates API version for global API
   *
   * @param api API sent by controller
   * @return API version
   */
  private static String getVersion(API api) {
    if (cellConfig.getVersion().equals(Constants.Utils.EMPTY_STRING)) {
      return api.getVersion();
    } else {
      return cellConfig.getVersion();
    }
  }
}
