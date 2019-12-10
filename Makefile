#  Copyright (c) 2019 WSO2 Inc. (http:www.wso2.org) All Rights Reserved.
#
#  WSO2 Inc. licenses this file to you under the Apache License,
#  Version 2.0 (the "License"); you may not use this file except
#  in compliance with the License.
#  You may obtain a copy of the License at
#
#  http:www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.

PROJECT_ROOT := $(realpath $(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
GIT_REVISION := $(shell git rev-parse --verify HEAD)
DOCKER_REPO ?= wso2cellery
DOCKER_IMAGE_TAG ?= $(GIT_REVISION)


all: clean build docker


.PHONY: clean
clean:
	mvn clean -f pom.xml


.PHONY: build
build:
	mvn install -f components/pom.xml


.PHONY: docker
docker: 
	[ -d "docker/global-apim/target" ] || mvn initialize -f docker/pom.xml
	cd docker/global-apim; \
	docker build -t ${DOCKER_REPO}/wso2am:${DOCKER_IMAGE_TAG} .
#	cd docker/microgateway/init-container; \
#	docker build -t ${DOCKER_REPO}/cell-gateway-init:${DOCKER_IMAGE_TAG} .
#	cd docker/microgateway/microgateway-container; \
#	docker build -t ${DOCKER_REPO}/cell-gateway:${DOCKER_IMAGE_TAG} .
	cd docker/lightweight-idp; \
	docker build -t ${DOCKER_REPO}/wso2is-lightweight:${DOCKER_IMAGE_TAG} .
	cd docker/api-publisher; \
	docker build -t ${DOCKER_REPO}/api-publisher:${DOCKER_IMAGE_TAG} .


.PHONY: docker-push
docker-push: docker
#	docker push ${DOCKER_REPO}/cell-gateway:${DOCKER_IMAGE_TAG}
#	docker push ${DOCKER_REPO}/cell-gateway-init:${DOCKER_IMAGE_TAG}
	docker push ${DOCKER_REPO}/wso2am:${DOCKER_IMAGE_TAG}
	docker push ${DOCKER_REPO}/wso2is-lightweight:${DOCKER_IMAGE_TAG}
	docker push ${DOCKER_REPO}/api-publisher:${DOCKER_IMAGE_TAG}

