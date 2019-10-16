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
release_version=master
release_archive_version=master

#Download k8s artifacts
mkdir ${download_path}
distribution_url=${GIT_DISTRIBUTION_URL:-https://github.com/wso2-cellery/distribution/archive}
wget ${distribution_url}/${release_archive_version}.zip -O ${download_path}/${release_version}.zip -a cellery-setup.log
unzip ${download_path}/${release_version}.zip -d ${download_path}

mesh_observability_url=${GIT_MESH_OBSERVABILITY_URL:-https://github.com/wso2-cellery/mesh-observability/archive}
wget ${mesh_observability_url}/${release_archive_version}.zip -O ${download_path}/${release_version}.zip -a cellery-setup.log
unzip ${download_path}/${release_version}.zip -d ${download_path}

#Create folders required by the mysql PVC
if [ -d /mnt/mysql ]; then
    mv /mnt/mysql "/mnt/mysql.$(date +%s)"
fi
mkdir -p /mnt/mysql
#Change the folder ownership to mysql server user.
chown 999:999 /mnt/mysql/cellery_runtime_mysql

if [ -d /mnt/apim_repository_deployment_server ]; then
    mv /mnt/apim_repository_deployment_server "/mnt/apim_repository_deployment_server.$(date +%s)"
fi
#Create folders required by the APIM PVC
mkdir -p /mnt/apim_repository_deployment_server
chown 802:802 /mnt/apim_repository_deployment_server

HOST_NAME=$(hostname | tr '[:upper:]' '[:lower:]')
#label the node if k8s provider is kubeadm
kubectl label nodes $HOST_NAME disk=local

# Install the Cellery runtime
kubectl create -f /home/vagrant/rbac-config.yaml
helm init --upgrade --service-account tiller
sleep 120

# Install istio
curl -L https://git.io/getLatestIstio | ISTIO_VERSION=1.2.2 sh -
cd istio-1.2.2/
helm install install/kubernetes/helm/istio-init --name istio-init --namespace istio-system
sleep 60
crd_count=$(kubectl get crds | grep 'istio.io\|certmanager.k8s.io' | wc -l)
#if [[ crd_count -eq 23 ]]; then
helm install install/kubernetes/helm/istio --name istio --namespace istio-system --values install/kubernetes/helm/istio/values-istio-demo.yaml
sleep 30
echo "Istio installation is finished"
#fi
cd ..

#Enabling Istio injection
kubectl label namespace default istio-injection=enabled

# Install Knative CRDs
helm install --name knative-crd ${download_path}/distribution-${release_version}/installer/helm/knative-crd
sleep 120
# Install Knative
# Knative is disabled for minimum installation.
#helm install --name knative ${download_path}/distribution-${release_version}/installer/helm/knative

# Install Cellery control plane
helm install --name cellery-runtime ${download_path}/distribution-${release_version}/installer/helm/cellery-runtime -f /home/vagrant/cellery-runtime-values.yaml

# Install ingress controller
cd ${download_path}/distribution-${release_version}/installer/helm/ingress-controller/
mkdir charts
cd charts
helm fetch stable/nginx-ingress
cd ../../../../../../
helm install --name ingress-controller ${download_path}/distribution-${release_version}/installer/helm/ingress-controller --namespace ingress-controller -f /home/vagrant/ingress-controller-values.yaml