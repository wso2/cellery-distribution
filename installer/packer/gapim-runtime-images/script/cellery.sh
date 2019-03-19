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

#Create folders required by the mysql PVC
if [ -d /mnt/mysql ]; then
    mv /mnt/mysql "/mnt/mysql.$(date +%s)"
fi
mkdir -p /mnt/mysql
#Change the folder ownership to mysql server user.
chown 999:999 /mnt/mysql

if [ -d /mnt/apim_repository_deployment_server ]; then
    mv /mnt/apim_repository_deployment_server "/mnt/apim_repository_deployment_server.$(date +%s)"
fi
#Create folders required by the APIM PVC
mkdir -p /mnt/apim_repository_deployment_server
chown 802:802 /mnt/apim_repository_deployment_server

declare -A config_params
config_params["MYSQL_DATABASE_HOST"]="wso2apim-with-analytics-rdbms-service"
config_params["DATABASE_USERNAME"]="cellery"
db_passwd=$(cat /dev/urandom | env LC_CTYPE=C tr -dc a-zA-Z0-9 | head -c 16; echo)
config_params["DATABASE_PASSWORD"]=$db_passwd

for param in "${!config_params[@]}"
do
    sed -i "s/$param/${config_params[$param]}/g" ${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/datasources/master-datasources.xml
#    sed -i "s/$param/${config_params[$param]}/g" ${download_path}/distribution-${release_version}/installer/k8s-artefacts/observability/sp/conf/deployment.yaml
#    sed -i "s/$param/${config_params[$param]}/g" ${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-idp/conf/datasources/master-datasources.xml
done

for param in "${!config_params[@]}"
do
    sed -i "s/$param/${config_params[$param]}/g" ${download_path}/distribution-${release_version}/installer/k8s-artefacts/mysql/dbscripts/init.sql
done

#Deploy Cellery k8s artifacts
#Create Cellery ns
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/system/ns-init.yaml

HOST_NAME=$(hostname | tr '[:upper:]' '[:lower:]')
#label the node if k8s provider is kubeadm
kubectl label nodes $HOST_NAME disk=local

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

kubectl create configmap mysql-dbscripts --from-file=${download_path}/distribution-${release_version}/installer/k8s-artefacts/mysql/dbscripts/ -n cellery-system
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/mysql/mysql-persistent-volumes-local.yaml -n cellery-system
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/mysql/mysql-persistent-volume-claim.yaml -n cellery-system
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/mysql/mysql-deployment.yaml -n cellery-system
#Wait till the mysql deployment availability
kubectl wait deployment/wso2apim-with-analytics-mysql-deployment --for condition=available --timeout=6000s -n cellery-system
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/mysql/mysql-service.yaml -n cellery-system 

#Create apim local volumes and volume claims
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-apim/persistent-volume-local.yaml -n cellery-system
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-apim/persistent-volume-claim-local.yaml -n cellery-system

#Create the gw config maps
kubectl create configmap gw-conf --from-file=${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf -n cellery-system
kubectl create configmap gw-conf-datasources --from-file=${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/datasources/ -n cellery-system

#Create KM config maps
kubectl create configmap conf-identity --from-file=${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/identity -n cellery-system
kubectl create configmap apim-template --from-file=${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/resources/api_templates -n cellery-system
kubectl create configmap apim-tomcat --from-file=${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/tomcat -n cellery-system
kubectl create configmap apim-security --from-file=${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/security -n cellery-system

#Create gateway deployment and the service
kubectl apply -f ${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-apim/global-apim.yaml -n cellery-system
#Wait till the gateway deployment availability
kubectl wait deployment.apps/gateway --for condition=available --timeout=6000s -n cellery-system
