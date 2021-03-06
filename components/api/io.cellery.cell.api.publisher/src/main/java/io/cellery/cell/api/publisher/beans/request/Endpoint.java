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

package io.cellery.cell.api.publisher.beans.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.cellery.cell.api.publisher.utils.Constants;

/** Represents endpoint information. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Endpoint {

  @JsonProperty(Constants.JsonParamNames.PRODUCTION_ENDPOINTS)
  private ProductionEndpoint productionEndPoint;

  @JsonProperty(Constants.JsonParamNames.SANDBOX_ENDPOINTS)
  private SandboxEndpoint sandboxEndPoint;

  @JsonProperty(Constants.JsonParamNames.ENDPOINT_TYPE)
  private String endpointType;

  public Endpoint() {
    this.endpointType = "http";
  }

  public ProductionEndpoint getProductionEndPoint() {
    return productionEndPoint;
  }

  public void setProductionEndPoint(ProductionEndpoint productionEndPoint) {
    this.productionEndPoint = productionEndPoint;
  }

  public String getEndpointType() {
    return endpointType;
  }

  public void setEndpointType(String endpointType) {
    this.endpointType = endpointType;
  }

  public SandboxEndpoint getSandboxEndPoint() {
    return sandboxEndPoint;
  }

  public void setSandboxEndPoint(SandboxEndpoint sandboxEndPoint) {
    this.sandboxEndPoint = sandboxEndPoint;
  }
}
