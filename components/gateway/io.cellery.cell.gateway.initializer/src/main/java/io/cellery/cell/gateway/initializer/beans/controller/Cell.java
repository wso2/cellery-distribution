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

package io.cellery.cell.gateway.initializer.beans.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.cellery.cell.gateway.initializer.utils.Constants;

import java.util.List;

/**
 * Class to represent Cell
 */
public class Cell {

    @JsonProperty(Constants.JsonParamNames.CELL)
    private String cell;

    @JsonProperty(Constants.JsonParamNames.VERSION)
    private String version;

    @JsonProperty(Constants.JsonParamNames.APIS)
    private List<API> apis;

    @JsonProperty(Constants.JsonParamNames.HOSTNAME)
    private String hostname;

    public String getCell() {
        return cell;
    }

    public void setCell(String cell) {
        this.cell = cell;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<API> getApis() {
        return apis;
    }

    public void setApis(List<API> apis) {
        this.apis = apis;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
}
