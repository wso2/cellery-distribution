#!/usr/bin/env bash
# ------------------------------------------------------------------------
#
# Copyright 2019 WSO2, Inc. (http://wso2.com)
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

download_path=${DOWNLOAD_PATH:-tmp-cellery}
distribution_url=${GIT_DISTRIBUTION_URL:-https://github.com/wso2-cellery/distribution/archive}
release_version=${RELEASE_VERSION:-master}

#Download k8s artifacts
mkdir ${download_path}
wget ${distribution_url}/${release_version}.zip -O ${download_path}/${release_version}.zip -a cellery-setup.log
unzip ${download_path}/${release_version}.zip -d ${download_path}

#Deploy Cellery k8s artifacts
#Create Cellery ns
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/system/ns-init.yaml
#Create Istio deployment
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/system/istio-crds.yaml
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/system/istio-demo-cellery.yaml
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/system/istio-gateway.yaml
sleep 360
#Create Cellery CRDs.
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/controller/01-cluster-role.yaml
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/controller/02-service-account.yaml
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/controller/03-cluster-role-binding.yaml
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/controller/04-crd-cell.yaml
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/controller/05-crd-gateway.yaml
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/controller/06-crd-token-service.yaml
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/controller/07-crd-service.yaml
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/controller/08-config.yaml
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/controller/09-controller.yaml
sleep 120

