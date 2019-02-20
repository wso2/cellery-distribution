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

package io.cellery.cell.gateway.initializer.internals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cellery.cell.gateway.initializer.beans.controller.APIMConfig;
import io.cellery.cell.gateway.initializer.utils.RequestProcessor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.cellery.cell.gateway.initializer.beans.controller.Cell;
import io.cellery.cell.gateway.initializer.beans.controller.RestConfig;
import io.cellery.cell.gateway.initializer.exceptions.APIException;
import io.cellery.cell.gateway.initializer.utils.Constants;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Methods to read the configuration files
 */
public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static volatile Cell cell = null;
    private static volatile RestConfig restConfig = null;
    private static volatile APIMConfig apimConfig = null;

    /**
     * Initializes the Cell configuration
     */
    private static void cellInitialize() throws IOException {
        synchronized (ConfigManager.class) {
            if (cell == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Loading cell configuration..");
                }
                cell = loadCellConfig();
            }
        }
    }

    /**
     * Initializes the REST configuration
     */
    private static void restConfigInitialize() throws IOException {
        synchronized (ConfigManager.class) {
            if (restConfig == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Loading global configuration..");
                }
                restConfig = loadRESTConfig();
            }
        }
    }

    /**
     * Initializes the Cell configuration
     */
    private static void apimInitialize() throws APIException {
        synchronized (ConfigManager.class) {
            if (apimConfig == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Loading apim configuration..");
                }
                loadApimConfig();
            }
        }
    }

    /**
     * Returns the whole configuration as a {@link Cell} beans
     *
     * @return cell bean
     */
    public static Cell getCellConfiguration() throws APIException {
        try {
            if (cell == null) {
                cellInitialize();
            }
        } catch (IOException e) {
            String errorMessage = "Error occurred while initializing configuration.";
            log.error(errorMessage, e);
            throw new APIException(errorMessage, e);
        }
        return cell;
    }

    /**
     * Returns the whole configuration as a {@link RestConfig} beans
     *
     * @return REST bean
     */
    public static RestConfig getRestConfiguration() throws APIException {
        try {
            if (restConfig == null) {
                restConfigInitialize();
            }
        } catch (IOException e) {
            String errorMessage = "Error occurred while initializing configuration.";
            log.error(errorMessage, e);
            throw new APIException(errorMessage, e);
        }
        return restConfig;
    }

    /**
     * Returns the whole configuration as a {@link APIMConfig} beans
     *
     * @return REST bean
     */
    public static APIMConfig getAPIMConfiguration() throws APIException {
        if (apimConfig == null) {
            apimInitialize();
        }
        return apimConfig;
    }

    /**
     * Load Cell configuration
     *
     * @return Cell Config
     */
    private static Cell loadCellConfig() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Reading cell configuration file: " + Constants.Utils.CELL_CONFIGURATION_FILE_PATH);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new File(Constants.Utils.CELL_CONFIGURATION_FILE_PATH), Cell.class);
    }

    /**
     * Load configurations required for REST APIs
     *
     * @return REST Config
     */
    private static RestConfig loadRESTConfig() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Reading global configuration file: " + Constants.Utils.REST_CONFIGURATION_FILE_PATH);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new File(Constants.Utils.REST_CONFIGURATION_FILE_PATH), RestConfig.class);
    }

    /**
     * Load APIM configuration required for getting Access token
     */
    private static void loadApimConfig() throws APIException {
        if (log.isDebugEnabled()) {
            log.debug("Loading APIM Configs");
        }
        apimConfig = new APIMConfig();
        String username = restConfig.getUsername();
        String password = restConfig.getPassword();
        byte[] message = (username + ":" + password).getBytes(StandardCharsets.UTF_8);
        String userAuth = Base64.getEncoder().encodeToString(message);
        generateClientIDSecret(userAuth);
        generateAccessToken();
    }

    /**
     * Generate Client ID and Client Secret.
     *
     * @param authHeader Authorization Header
     * @throws APIException Throw an exception if any error occurred.
     */
    private static void generateClientIDSecret(String authHeader) throws APIException {
        if (log.isDebugEnabled()) {
            log.debug("Calling the dynamic client registration endpoint...");
        }
        RequestProcessor requestProcessor = new RequestProcessor();
        String apimBaseURL = restConfig.getApimBaseUrl();
        String applicationResponse = requestProcessor
                .doPost(apimBaseURL + Constants.Utils.PATH_CLIENT_REGISTRATION + restConfig.getApiVersion() +
                                Constants.Utils.PATH_REGISTER, Constants.Utils.CONTENT_TYPE_APPLICATION_JSON,
                        Constants.Utils.CONTENT_TYPE_APPLICATION_JSON, Constants.Utils.BASIC + authHeader,
                        restConfig.getRegisterPayload().toJSONString());

        if (applicationResponse != null) {
            JSONObject jsonObj = new JSONObject(applicationResponse);
            apimConfig.setClientId(jsonObj.getString(Constants.Utils.CLIENT_ID));
            apimConfig.setClientSecret(jsonObj.getString(Constants.Utils.CLIENT_SECRET));
        }
    }

    /**
     * Generate access tokens required to invoke RESTful APIs
     *
     * @throws APIException throw API Exception if an error occurred while generating an access token.
     */
    private static void generateAccessToken() throws APIException {
        if (log.isDebugEnabled()) {
            log.debug("Calling token endpoint to generate access tokens...");
        }

        String tokenPayload = Constants.Utils.TOKEN_PAYLOAD.replace("$USER", restConfig.getUsername())
                .replace("$PASS", restConfig.getPassword());
        apimConfig.setApiToken(getToken(tokenPayload));
    }

    /**
     * Invoke Rest API to get token
     *
     * @param tokenPayload Post payload
     * @return access token
     * @throws APIException throw API Exception if an error occurred
     */
    private static String getToken(String tokenPayload) throws APIException {
        RequestProcessor requestProcessor = new RequestProcessor();
        String auth = getBase64EncodedClientIdAndSecret();
        String apiCreateTokenResponse = requestProcessor
                .doPost(restConfig.getTokenEndpoint(), Constants.Utils.CONTENT_TYPE_APPLICATION_URL_ENCODED,
                        Constants.Utils.CONTENT_TYPE_APPLICATION_JSON, Constants.Utils.BASIC + auth, tokenPayload);

        if (apiCreateTokenResponse != null) {
            JSONObject jsonObj = new JSONObject(apiCreateTokenResponse);
            return jsonObj.getString(Constants.Utils.ACCESS_TOKEN);
        } else {
            throw new APIException(
                    "Error while generating the access token from token endpoint: " + restConfig.getTokenEndpoint());
        }
    }

    private static String getBase64EncodedClientIdAndSecret() {
        byte[] message = (apimConfig.getClientId() + ":" + apimConfig.getClientSecret()).getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(message);
    }

}

