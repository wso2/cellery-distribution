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

package io.cellery.cell.gateway.initializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cellery.cell.gateway.initializer.beans.controller.APIMConfig;
import io.swagger.models.Info;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.util.Json;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import io.cellery.cell.gateway.initializer.beans.controller.API;
import io.cellery.cell.gateway.initializer.beans.request.ApiCreateRequest;
import io.cellery.cell.gateway.initializer.beans.controller.ApiDefinition;
import io.cellery.cell.gateway.initializer.beans.controller.Cell;
import io.cellery.cell.gateway.initializer.beans.request.Endpoint;
import io.cellery.cell.gateway.initializer.beans.request.Method;
import io.cellery.cell.gateway.initializer.beans.request.Parameter;
import io.cellery.cell.gateway.initializer.beans.request.PathDefinition;
import io.cellery.cell.gateway.initializer.beans.request.PathsMapping;
import io.cellery.cell.gateway.initializer.beans.request.ProductionEndpoint;
import io.cellery.cell.gateway.initializer.beans.controller.RestConfig;
import io.cellery.cell.gateway.initializer.exceptions.APIException;
import io.cellery.cell.gateway.initializer.internals.ConfigManager;
import io.cellery.cell.gateway.initializer.utils.RequestProcessor;
import io.cellery.cell.gateway.initializer.utils.Constants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Class for create APIs in global API Manager.
 */
public class UpdateManager {

    private static Cell cellConfig;
    private static RestConfig restConfig;
    private static APIMConfig apimConfig;
    private static final Logger log = LoggerFactory.getLogger(UpdateManager.class);

    public static void main(String[] args) {
        try {
            // Encode username password to base64
            restConfig = ConfigManager.getRestConfiguration();
            cellConfig = ConfigManager.getCellConfiguration();

            List apiIds = createGlobalAPIs();
            publishGlobalAPIs(apiIds);
            generateApiConfigJson();
            log.info("Global API creation is completed successfully..");
            // Run microgateway setup command.
            microgatewaySetup();
            log.info("Microgateway setup success");
            microgatewayBuild();
            log.info("Microgateway build success");
            unzipTargetFile();
            moveUnzippedFolderToMountLocation();
            log.info("Init container configuration is completed successfully..");
        } catch (APIException e) {
            log.error("Error occurred while creating APIs in Global API manager. " + e.getMessage(), e);
            System.exit(Constants.Utils.ERROR_EXIT_CODE);
        } catch (IOException e) {
            log.error("Error occurred while configuring the microgateway.", e);
            System.exit(Constants.Utils.ERROR_EXIT_CODE);
        } catch (InterruptedException e) {
            log.error("Error occurred while waiting for the process completion", e);
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
                createAPIPath = restConfig.getApimBaseUrl() + Constants.Utils.PATH_PUBLISHER + restConfig.getApiVersion() +
                        Constants.Utils.PATH_APIS;
                apiCreateResponse = requestProcessor
                        .doPost(createAPIPath, Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
                                Constants.Utils.CONTENT_TYPE_APPLICATION_JSON, Constants.Utils.BEARER + apimConfig.getApiToken(),
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
        API[] apis = cellConfig.getApis();

        for (API api : apis) {
            if (api.isGlobal()) {
                // Create api payload with gateway backend
                ApiCreateRequest globalApiCreateRequest = new ApiCreateRequest();
                globalApiCreateRequest.setName(generateAPIName(api));
                globalApiCreateRequest
                        .setContext((cellConfig.getCell() + "/" + api.getContext()).replaceAll("//", "/"));
                globalApiCreateRequest.setVersion(cellConfig.getVersion());
                globalApiCreateRequest.setApiDefinition(getAPIDefinition(api));
                globalApiCreateRequest.setEndpointConfig(getGlobalEndpoint(api));
                globalApiCreateRequest.setGatewayEnvironments(Constants.Utils.PRODUCTION_AND_SANDBOX);

                // Set some additional properties.
                Map<String, String> additionalProperties = new HashMap<>();
                additionalProperties.put(Constants.Utils.CELL_NAME_PROPERTY, cellConfig.getCell());
                globalApiCreateRequest.setAdditionalProperties(additionalProperties);

                apiPayloadsArray.put(globalApiCreateRequest);
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
        if (log.isDebugEnabled()) {
            log.debug("Publishing created APIs in Global API Manager...");
        }

        for (Object id : ids) {
            RequestProcessor requestProcessor = new RequestProcessor();
            String apiPublishResponse;
            String apiPublishPath = restConfig.getApimBaseUrl() + Constants.Utils.PATH_PUBLISHER + restConfig.getApiVersion() +
                    Constants.Utils.PATH_LIFECYCLE + "apiId=" + id + "&action=Publish";
            apiPublishResponse = requestProcessor.doPost(apiPublishPath, Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
                    Constants.Utils.CONTENT_TYPE_APPLICATION_JSON, Constants.Utils.BEARER + apimConfig.getApiToken(),
                    Constants.Utils.EMPTY_STRING);

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
        productionEndpoint.setUrl(Constants.Utils.HTTP + cellConfig.getHostname()+"/"+api.getContext());

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
        ApiDefinition[] definitions = api.getDefinitions();

        for (ApiDefinition definition : definitions) {
            PathDefinition pathDefinition;
            Method method = new Method();
            String methodStr = definition.getMethod();

            // Append /* to allow query parameters and path parameters
            String allowQueryPath = definition.getPath().replaceAll("/$", Constants.Utils.EMPTY_STRING) +
                    Constants.Utils.ALLOW_QUERY_PATTERN;

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
                    throw new APIException("Method: " + methodStr + "is not implemented");
            }
            apiDefinition.addPathDefinition(allowQueryPath, pathDefinition);
        }
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
     * Run microgateway setup command to create build artifacts.
     */
    private static void microgatewaySetup() throws IOException, InterruptedException {
        ProcessBuilder processBuilder =
                new ProcessBuilder(Constants.Utils.MICROGATEWAY_PATH, "setup", cellConfig.getCell(),
                        "-ms", Constants.Utils.API_CONFIG_PATH);

        Process process = processBuilder.start();
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        String line;
        while ((line = inputReader.readLine()) != null) {
            log.info(line);
        }
        while ((line = errorReader.readLine()) != null) {
            log.error(line);
        }
        process.waitFor();
    }

    /**
     * Run microgateway build command.
     */
    private static void microgatewayBuild() throws IOException, InterruptedException {
        ProcessBuilder processBuilder =
                new ProcessBuilder(Constants.Utils.MICROGATEWAY_PATH, "build", cellConfig.getCell());
        Process process = processBuilder.start();
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        String line;
        while ((line = inputReader.readLine()) != null) {
            log.info(line);
        }
        while ((line = errorReader.readLine()) != null) {
            log.error(line);
        }
        process.waitFor();
    }

    /**
     * Unzip microgateway target file.
     */
    private static void unzipTargetFile() throws IOException {
        String targetZipName = "micro-gw-" + cellConfig.getCell() + ".zip";
        String targetZipFilePath = Constants.Utils.HOME_PATH + cellConfig.getCell() + "/target/" + targetZipName;
        System.out.println(targetZipFilePath);

        //create output directory is not exists
        File targetFolder = new File(Constants.Utils.UNZIP_FILE_PATH);
        if (!targetFolder.exists()) {
            if (!targetFolder.mkdir()) {
                log.warn("Failed to create folder: " + targetFolder);
            }
        }

        byte[] buffer = new byte[1024];
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(targetZipFilePath));
        ZipEntry zipEntry = zipInputStream.getNextEntry();
        while (zipEntry != null) {
            String fileName = zipEntry.getName();
            File newFile = new File(targetFolder + "/" + fileName);

            if (!newFile.getParentFile().exists()) {
                if (!newFile.getParentFile().mkdirs()) {
                    log.warn("Failed to create parent folders to create file: " + newFile);
                }
            }
            FileOutputStream fileOutputStream = new FileOutputStream(newFile);
            int len;
            while ((len = zipInputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, len);
            }
            fileOutputStream.close();
            zipEntry = zipInputStream.getNextEntry();
        }
        zipInputStream.closeEntry();
        zipInputStream.close();

        log.info("Unzipping microgateway target file is completed successfully..");
    }

    /**
     * Move unzipped folder to mount location.
     */
    private static void moveUnzippedFolderToMountLocation() throws IOException {
        String targetFolderName = "micro-gw-" + cellConfig.getCell();
        File sourceFolder = new File(Constants.Utils.UNZIP_FILE_PATH + "/" + targetFolderName);
        File destinationFolder = new File(Constants.Utils.MOUNT_FILE_PATH);
        FileUtils.copyDirectory(sourceFolder, destinationFolder);

        File file = new File(Constants.Utils.MOUNT_FILE_PATH + "/bin/gateway");
        file.setExecutable(true, false);

        log.info("Moved the unzipped folder to mount location successfully..");
    }

    /**
     * Writes API details in to API Config File
     *
     * @param api API being written
     */
    private static void writeToMicroGWConfig(API api) {
        JSONArray apiConfigArray = getApiConfig();
        JSONObject apiConfig = new JSONObject();
        apiConfig.put("swaggerPath", Constants.Utils.SWAGGER_FOLDER + removeSpecialChars(api.getBackend() + api.getContext()) + ".json");
        apiConfig.put("endpoint", api.getBackend());
        apiConfig.put("defaultAPI", true);
        apiConfigArray.put(apiConfig);
        writeToAFile(Constants.Utils.API_CONFIG_PATH, apiConfigArray.toString());
    }

    /**
     * Gets API Config file from the disk
     *
     * @return Apiconfig JSONArray
     */
    private static JSONArray getApiConfig() {
        JSONArray apiConfigArray = null;
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get(Constants.Utils.API_CONFIG_PATH)));
            apiConfigArray = new JSONArray(jsonContent);
        } catch (NoSuchFileException e) {
            createEmptyJSONArray();
            apiConfigArray = getApiConfig();
        } catch (IOException e) {
            e.printStackTrace();
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
            Files.createDirectories(pathToFile.getParent());
            Files.write(pathToFile, content.getBytes(), StandardOpenOption.CREATE);
        } catch (IOException e) {
            e.printStackTrace();
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
        API[] apis = cellConfig.getApis();
        for (API api : apis) {
            createSwagger(api);
            writeToMicroGWConfig(api);
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
        writeSwagger(swaggerString, removeSpecialChars(api.getBackend() + api.getContext()));
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
            Path path = new Path();
            Operation op = new Operation();

            Map<String, Response> resMap = new HashMap<>();
            Response res = new Response();
            res.setDescription("Successful");

            resMap.put("200", res);
            op.setResponses(resMap);

            disableMicroGWAuth(op);
            switch (definition.getMethod().toLowerCase()) {
                case Constants.JsonParamNames.GET:
                    path.setGet(op);
                    break;
                case Constants.JsonParamNames.POST:
                    path.setPost(op);
                    break;
                default:
                    log.error("HTTP Method not implemented");
            }
            pathMap.put(definition.getPath(), path);
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
}
