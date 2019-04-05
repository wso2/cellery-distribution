# ------------------------------------------------------------------------
#
# Copyright 2018 WSO2, Inc. (http://wso2.com)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License
#
# ------------------------------------------------------------------------

FROM wso2cellery/wso2is-lightweight-base:5.8.0

ARG USER_HOME=/home/wso2carbon
ARG FILES=./target/files

RUN mkdir ${USER_HOME}/wso2is-lightweightv1/repository/deployment/server/webapps/authenticationendpoint

RUN cd ${USER_HOME}/wso2is-lightweightv1/repository/deployment/server/webapps/authenticationendpoint ; jar -xvf ${USER_HOME}/wso2is-lightweightv1/repository/deployment/server/webapps/authenticationendpoint.war

COPY --chown=wso2carbon:wso2 ${FILES}/authenticationendpoint/ ${USER_HOME}/wso2is-lightweightv1/repository/deployment/server/webapps/authenticationendpoint/