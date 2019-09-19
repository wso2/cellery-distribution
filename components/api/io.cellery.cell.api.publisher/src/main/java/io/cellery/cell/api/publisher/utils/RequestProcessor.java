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

package io.cellery.cell.api.publisher.utils;

import io.cellery.cell.api.publisher.exceptions.APIException;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Utility methods for HTTP request processors
 */
public class RequestProcessor {

    private static final Logger log = LoggerFactory.getLogger(RequestProcessor.class);
    private CloseableHttpClient httpClient;

    public RequestProcessor() throws APIException {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring SSL verification...");
            }
            SSLContext sslContext = SSLContext.getInstance("SSL");

            X509TrustManager x509TrustManager = new TrustAllTrustManager();
            sslContext.init(null, new TrustManager[] {x509TrustManager}, new SecureRandom());

            SSLConnectionSocketFactory sslsocketFactory =
                    new SSLConnectionSocketFactory(sslContext, new String[] { "TLSv1.2" }, null,
                            (s, sslSession) -> true);

            httpClient = HttpClients.custom().setSSLSocketFactory(sslsocketFactory).build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            String errorMessage = "Error occurred while ignoring ssl certificates to allow http connections";
            log.error(errorMessage, e);
            throw new APIException(errorMessage, e);
        }
    }

    /**
     * Execute http get request.
     *
     * @param url         url
     * @param contentType content type
     * @param acceptType  accept type
     * @param authHeader  authorization header
     * @return Closable http response
     * @throws APIException Api exception when an error occurred
     */
    public CloseableHttpResponse doGet(String url, String contentType, String acceptType, String authHeader)
            throws APIException {

        CloseableHttpResponse response;
        try {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader(Constants.Utils.HTTP_CONTENT_TYPE, contentType);
            httpGet.setHeader(Constants.Utils.HTTP_RESPONSE_TYPE_ACCEPT, acceptType);
            httpGet.setHeader(Constants.Utils.HTTP_REQ_HEADER_AUTHZ, authHeader);

            response = httpClient.execute(httpGet);
            closeClientConnection();
        } catch (IOException e) {
            String errorMessage = "Error occurred while executing the http Get connection.";
            log.error(errorMessage, e);
            throw new APIException(errorMessage, e);
        }
        return response;
    }

    /**
     * Execute http post request
     *
     * @param url         url
     * @param contentType content type
     * @param acceptType  accept type
     * @param authHeader  authorization header
     * @param payload     post payload
     * @return Closable http response
     * @throws APIException Api exception when an error occurred
     */
    public String doPost(String url, String contentType, String acceptType, String authHeader, String payload)
            throws APIException {
        String returnObj = null;
        try {
            if (log.isDebugEnabled()) {
                log.debug("Post payload: " + payload);
                log.debug("Post auth header: " + authHeader);
            }
            StringEntity payloadEntity = new StringEntity(payload);
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader(Constants.Utils.HTTP_CONTENT_TYPE, contentType);
            httpPost.setHeader(Constants.Utils.HTTP_RESPONSE_TYPE_ACCEPT, acceptType);
            httpPost.setHeader(Constants.Utils.HTTP_REQ_HEADER_AUTHZ, authHeader);
            httpPost.setEntity(payloadEntity);

            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            String responseStr = EntityUtils.toString(entity);
            int statusCode = response.getStatusLine().getStatusCode();

            if (log.isDebugEnabled()) {
                log.debug("Response status code: " + statusCode);
                log.debug("Response string : " + responseStr);
            }
            if (responseValidate(statusCode, responseStr)) {
                returnObj = responseStr;
            }
            closeClientConnection();
        } catch (IOException e) {
            String errorMessage = "Error occurred while executing the http Post connection.";
            log.error(errorMessage, e);
            throw new APIException(errorMessage, e);
        }
        return returnObj;
    }

    /**
     * Close http client connection
     *
     * @throws IOException throws an IO exception if an error occurred while closing the connection.
     */
    private void closeClientConnection() throws IOException {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.error("Error while closing the http client connection", e);
                throw e;
            }
        }
    }

    /**
     * Validate the http response.
     * @param statusCode status code
     * @return boolean to validate response
     */
    private boolean responseValidate(int statusCode, String response) throws IOException {
        boolean isValid = false;
        switch (statusCode) {
            case HttpURLConnection.HTTP_OK:
                isValid = true;
                break;
            case HttpURLConnection.HTTP_CREATED:
                isValid = true;
                break;
            case HttpURLConnection.HTTP_ACCEPTED:
                isValid = true;
                break;
            case HttpURLConnection.HTTP_BAD_REQUEST:
                if (response != null && !Constants.Utils.EMPTY_STRING.equals(response)) {
                    if (response.contains(Constants.Utils.DIFFERENT_CONTEXT_ERROR) ||
                        response.contains(Constants.Utils.DUPLICATE_CONTEXT_ERROR)) {
                        // skip the error when trying to add the same api with different context.
                        isValid = true;
                    }
                }
                break;
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                isValid = false;
                break;
            case HttpURLConnection.HTTP_NOT_FOUND:
                isValid = false;
                break;
            case HttpURLConnection.HTTP_CONFLICT:
                if (response != null && !Constants.Utils.EMPTY_STRING.equals(response)) {
                    if (response.contains(Constants.Utils.DUPLICATE_API_ERROR)) {
                        // skip the error when trying to add the same api.
                        isValid = true;
                    }
                }
                break;
            case HttpURLConnection.HTTP_INTERNAL_ERROR:
                if (response != null && !Constants.Utils.EMPTY_STRING.equals(response)) {
                    if (response.contains(Constants.Utils.DUPLICATE_LABEL_ERROR)) {
                        // skip the error when trying to add the same label.
                        isValid = true;
                    }
                }
                break;
            default:
                isValid = false;
        }
        return isValid;
    }

    /**
     * Trust Manager which trusts all certificates.
     */
    public static class TrustAllTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
