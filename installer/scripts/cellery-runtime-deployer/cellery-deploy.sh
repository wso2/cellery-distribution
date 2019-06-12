#!/usr/bin/env bash
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

# How to deploy Cellery with kubeadm K8s provider.
# cat cellery-deploy.sh | bash -s -- <k8s Provier>
# cat cellery-deploy.sh | bash -s -- kubeadm
#
# How to deploy Cellery with GCP K8s provider.
# cat cellery-deploy.sh | bash -s -- <k8s Provier> <GCP Project ID> <GCP Compute Zone>
# Eg. cat cellery-deploy.sh | bash -s -- GCP proj-cellery us-west1-c
#
# User can override the addition configs via cellery.env
#
function install_k8s_kubeadm () {
local K8S_VERSION=$1

local UBUNTU_VERSION=$(cat /etc/lsb-release | sed -n '/DISTRIB_RELEASE/p' | awk -F'=' '{print $2}')

#if you get an error similar to
#'[ERROR Swap]: running with swap on is not supported. Please disable swap', disable swap:
sudo swapoff -a
# install some utils
sudo apt-get install -y apt-transport-https ca-certificates curl software-properties-common
#Install Docker
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
if [ $UBUNTU_VERSION == "16.04" ]; then
    sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu xenial stable"
elif [ $UBUNTU_VERSION == "18.04" ]; then
    sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu bionic stable"
else
    #default tested version
    sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu xenial stable"
fi
sudo apt-get update -qq
sudo apt-get install -y docker-ce=18.06.0~ce~3-0~ubuntu
#Install NFS client
sudo apt-get install -y nfs-common
#Enable docker service
sudo systemctl enable docker.service
#Update the apt source list
curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
sudo add-apt-repository "deb [arch=amd64] http://apt.kubernetes.io/ kubernetes-xenial main"
#Install K8s components
echo "⚙️ Installing kubeadm"
sudo apt-get install -y kubelet=$K8S_VERSION kubeadm=$K8S_VERSION kubectl=$K8S_VERSION
    sudo apt-mark hold kubelet kubeadm kubectl
}

function configure_k8s_kubeadm () {
local node_type=$1

if [ -z $node_type ]; then
    node_type="master"
fi

if [ $node_type == "master" ]; then
    #Initialize the k8s cluster
    echo "⚙️ Installing k8s using kubeadm"
    sudo kubeadm init --pod-network-cidr=10.244.0.0/16
    #Wait till k8s cluster initialization.
    sleep 60

    #Create .kube file if it does not exists
    mkdir -p $HOME/.kube
    #Move Kubernetes config file if it exists
    if [ -f $HOME/.kube/config ]; then
        mv $HOME/.kube/config $HOME/.kube/config.back
    fi

    sudo cp -f /etc/kubernetes/admin.conf $HOME/.kube/config
    sudo chown $(id -u):$(id -g) $HOME/.kube/config

    #if you are using a single node which acts as both a master and a worker
    #untaint the node so that pods will get scheduled:
    kubectl taint nodes --all node-role.kubernetes.io/master-

    #Install Flannel network
    kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/v0.10.0/Documentation/kube-flannel.yml

    #Install admission plugins
    echo "Installing K8s admission plugins"
    sudo sed -i 's/--enable-admission-plugins=NodeRestriction/--enable-admission-plugins=NamespaceLifecycle,LimitRanger,ServiceAccount,DefaultStorageClass,DefaultTolerationSeconds,NodeRestriction,MutatingAdmissionWebhook,ValidatingAdmissionWebhook,ResourceQuota/' /etc/kubernetes/manifests/kube-apiserver.yaml

    #Wait to restart the K8s with new admission plugins
    sleep 60
 echo "👣 k8s Master node installation is finished"

elif [ $node_type == "worker" ]; then
    read -p "Enter the Master node IP and the Token [master_node_ip token discovery_token_ca_cert_hash]:" master_node_ip token discovery_token_ca_cert_hash
    if [ -n "$master_node_ip" ] && [ -n "$token" ] && [ -n "$discovery_token_ca_cert_hash" ]; then
        echo $master_node_ip $token $discovery_token_ca_cert_hash
        #Add more worker nodes.
        sudo kubeadm join $master_node_ip:6443 --token $token --discovery-token-ca-cert-hash $discovery_token_ca_cert_hash
    else
        echo " Enter all three argument"
    fi
else
    echo "Enter correct arguments"
fi

}

#Create new NFS share
function create_nfs_share_gcp () {
local nfs_share_location=$1
local gcp_compute_zone=$2
local nfs_server_ip

echo "ℹ️ Creating NFS share in GCP"
gcloud beta filestore instances create nfs-server \
    --location=$gcp_compute_zone \
    --tier=STANDARD \
    --file-share=name="${nfs_share_location}",capacity=1TB \
    --network=name="default"
#Wati till NFS server creation finished.
sleep 30

nfs_server_ip=$(gcloud beta filestore instances describe nfs-server --location=$gcp_compute_zone \
--format flattened | awk '/ipAddresses/  {print $2}')

if [ -n $nfs_server_ip ]; then
    nfs_config_params["NFS_SERVER_IP"]=$nfs_server_ip
    nfs_config_params["NFS_SHARE_LOCATION"]="/${nfs_share_location}"
else
    echo "🙅 Error in NFS share creation"
    exit 0
fi
}

#Read NFS connection details form the user
function read_nfs_connection () {
local nfs_server_ip
local nfs_share_location

echo "ℹ️ Configuring NFS volume"
echo
read -p "⛏️ NFS server IP: " nfs_server_ip < /dev/tty
if [[ ! -z "${nfs_server_ip/ //}" ]]; then
    nfs_config_params["NFS_SERVER_IP"]=$nfs_server_ip
fi
read -p "⛏️ NFS share location: " nfs_share_location < /dev/tty
if [[ ! -z "${nfs_share_location/ //}" ]]; then
    nfs_config_params["NFS_SHARE_LOCATION"]=$nfs_share_location
fi
echo
}

#Update APIM PVs
function update_apim_nfs_volumes () {
local download_location=$1
local release_version=$2

darwin=false;
case "`uname`" in
  Darwin*) darwin=true ;;
esac

if $darwin; then
  sedi="/usr/bin/sed -i ''"
else
  sedi="sed -i"
fi

for param in "${!nfs_config_params[@]}"
do
    $sedi "s|$param|${nfs_config_params[$param]}|g" ${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/artifacts-persistent-volume.yaml
done
}

#Create K8s cluster in GCP
#User need to configure GCP CLI in the machine
function install_k8s_gcp () {
local gcp_project=$1
local gcp_compute_zone=$2
local gcp_k8s_cluster_name=$3
local k8s_version=$4
local gcp_k8s_cluster_machine_type=$5
local gcp_cluster_min_nodes=$6
local gcp_k8s_cluster_max_nodes=$7
local gcp_k8s_cluster_num_nodes=$8

#Point GCP to the new project
gcloud config set project $gcp_project
#gcloud config set compute/region REGION
gcloud config set compute/zone $gcp_compute_zone

#Enable required GCP APIs
gcloud services enable \
  cloudapis.googleapis.com \
  container.googleapis.com \
  containerregistry.googleapis.com

echo "ℹ️ Creating K8s cluster $gcp_k8s_cluster_name in zone $gcp_compute_zone"

#Create K8s cluster
gcloud -q --verbosity=error container clusters create $gcp_k8s_cluster_name \
  --cluster-version=$k8s_version \
  --machine-type=$gcp_k8s_cluster_machine_type \
  --enable-autoscaling --min-nodes=$gcp_cluster_min_nodes --max-nodes=$gcp_k8s_cluster_max_nodes \
  --enable-autorepair \
  --scopes=service-control,service-management,compute-rw,storage-ro,cloud-platform,logging-write,monitoring-write,pubsub,datastore \
  --num-nodes=$gcp_k8s_cluster_num_nodes

#Wait for 60 seconds to finish the k8s cluster creation.
sleep 60s

#Grant cluster-admin permissions to the current user
K8S_ADMIN=$(gcloud config get-value core/account)

echo "ℹ️ Grant k8s cluster admin permission to user $K8S_ADMIN"

kubectl create clusterrolebinding cluster-admin-binding \
--clusterrole=cluster-admin \
--user=$(gcloud config get-value core/account)
}

function deploy_mysql_server () {
local download_location=$1
local release_version=$2

#Create folders required by the mysql PVC
if [ -d /mnt/mysql ]; then
    sudo mv /mnt/mysql "/mnt/mysql.$(date +%s)"
fi
sudo mkdir -p /mnt/mysql
#Change the folder ownership to mysql server user.
sudo chown 999:999 /mnt/mysql

kubectl create configmap mysql-dbscripts --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/mysql/dbscripts/ -n cellery-system
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/mysql/mysql-persistent-volumes-local.yaml -n cellery-system
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/mysql/mysql-persistent-volume-claim.yaml -n cellery-system
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/mysql/mysql-deployment.yaml -n cellery-system
#Wait till the mysql deployment availability
kubectl wait deployment/wso2apim-with-analytics-mysql-deployment --for condition=available --timeout=300s -n cellery-system
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/mysql/mysql-service.yaml -n cellery-system
}

function deploy_mysql_server_gcp () {
local download_location=$1
local sql_instance_name=$2
local gcp_compute_zone=$3
local gcp_sql_tire=$4
local service_account
local mysql_server_ip
local $release_version=$5

gcloud -q sql instances create ${sql_instance_name} --tier=${gcp_sql_tire} --zone=${gcp_compute_zone}
service_account=$(gcloud beta sql instances describe ${sql_instance_name} --format flattened | awk '/serviceAccountEmailAddress/ {print $2}')
#TODO remove after the key length fix
gcloud -q sql instances patch ${sql_instance_name} --authorized-networks=0.0.0.0/0 --database-flags character_set_server=latin1
#Wait till finish the MySQL server restart after the patching
sleep 60
echo "⚙️ Setting MySQL server root user password."
gcloud sql users set-password root --instance=${sql_instance_name} --prompt-for-password --host=%
#Wait till the credential update sync.
sleep 30
cat ${download_location}/distribution-${release_version}/installer/k8s-artefacts/mysql/dbscripts/init.sql | gcloud sql connect ${sql_instance_name} --user=root

mysql_server_ip=$(gcloud beta sql instances describe ${sql_instance_name}  --format flattened | awk '/.ipAddress/ {print $2}')
config_params["MYSQL_DATABASE_HOST"]=$mysql_server_ip
}

#Configure remote mysql server endpoint in control plane configuration files.
function read_control_plane_datasources_configs () {
local db_user
local db_passwd
local db_hostname

if [ $iaas == "kubeadm" ] || [ $iaas == "k8s" ] || [ $iaas == "GCP" ]; then
    config_params["MYSQL_DATABASE_HOST"]="wso2apim-with-analytics-rdbms-service"
    config_params["DATABASE_USERNAME"]="cellery"
    db_passwd=$(cat /dev/urandom | env LC_CTYPE=C tr -dc a-zA-Z0-9 | head -c 16; echo)
    config_params["DATABASE_PASSWORD"]=$db_passwd
else
    echo "Configuring remote MySQL server"
    read -p "Database host name: " db_hostname < /dev/tty
    if [[ ! -z "${db_hostname/ //}" ]]; then
            echo "Db Hostname ${db_hostname}"
            config_params["MYSQL_DATABASE_HOST"]=$db_hostname
    fi
    read -p "⛏️ Database user name: " db_user < /dev/tty
    if [[ ! -z "${db_user/ //}" ]]; then
            config_params["DATABASE_USERNAME"]=$db_user
    fi
    read -s -p "⛏️ Database user password: " db_passwd < /dev/tty
    if [[ ! -z "${db_passwd/ //}" ]]; then
            config_params["DATABASE_PASSWORD"]=$db_passwd
    fi
fi
}

function update_control_plance_sql () {
local download_location=$1
local release_version=$2

darwin=false;
case "`uname`" in
  Darwin*) darwin=true ;;
esac

if $darwin; then
  sedi="/usr/bin/sed -i ''"
else
  sedi="sed -i"
fi

for param in "${!config_params[@]}"
do
    $sedi "s/$param/${config_params[$param]}/g" ${download_location}/distribution-${release_version}/installer/k8s-artefacts/mysql/dbscripts/init.sql
done
}

#Update pub-store/gw/sp worker/ sp dashboard datasources
function update_control_plane_datasources () {
local download_location=$1
local release_version=$2

darwin=false;
case "`uname`" in
  Darwin*) darwin=true ;;
esac

if $darwin; then
  sedi="/usr/bin/sed -i ''"
else
  sedi="sed -i"
fi

for param in "${!config_params[@]}"
do
    $sedi "s/$param/${config_params[$param]}/g" ${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/datasources/master-datasources.xml
    $sedi "s/$param/${config_params[$param]}/g" ${download_location}/distribution-${release_version}/installer/k8s-artefacts/observability/sp/conf/deployment.yaml
    $sedi "s/$param/${config_params[$param]}/g" ${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-idp/conf/datasources/master-datasources.xml
done
}

function deploy_global_gw () {
local download_location=$1
local iaas=$2
local release_version=$3

if [ $iaas == "kubeadm" ] || [ $iaas == "k8s" ]; then
    #Create folders required by the APIM GW PVC
    if [ -d /mnt/apim_repository_deployment_server ]; then
        sudo mv /mnt/apim_repository_deployment_server "/mnt/apim_repository_deployment_server.$(date +%s)"
    fi
    #Create folders required by the APIM PVC
    sudo mkdir -p /mnt/apim_repository_deployment_server
    sudo chown 802:802 /mnt/apim_repository_deployment_server
    #Create apim local volumes and volume claims
    kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/persistent-volume-local.yaml -n cellery-system
    kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/persistent-volume-claim-local.yaml -n cellery-system
elif [ $iaas == "GCP" ]; then
    #Create apim NFS volumes and volume claims
    kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/artifacts-persistent-volume.yaml -n cellery-system
    kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/artifacts-persistent-volume-claim.yaml -n cellery-system
fi

#Create the gw config maps
kubectl create configmap gw-conf --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf -n cellery-system
kubectl create configmap gw-conf-datasources --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/datasources/ -n cellery-system
#Create KM config maps
kubectl create configmap conf-identity --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/identity -n cellery-system
kubectl create configmap apim-template --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/resources/api_templates -n cellery-system
kubectl create configmap apim-tomcat --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/tomcat -n cellery-system
kubectl create configmap apim-security --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/conf/security -n cellery-system

#Create gateway deployment and the service
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-apim/global-apim.yaml -n cellery-system
#Wait till the gateway deployment availability
kubectl wait deployment.apps/gateway --for condition=available --timeout=300s -n cellery-system
}

function deploy_sp_dashboard_worker () {
local download_location=$1
local release_version=$2

#Create SP worker configmaps
kubectl create configmap sp-worker-siddhi --from-file=${download_location}/mesh-observability-${release_version}/components/global/core/io.cellery.observability.siddhi.apps/src/main/siddhi -n cellery-system
kubectl create configmap sp-worker-conf --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/observability/sp/conf -n cellery-system
#kubectl create configmap sp-worker-bin --from-file=${download_location}/sp-worker/bin -n cellery-system
#Create SP worker deployment
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/observability/sp/sp-worker.yaml -n cellery-system
#Create SP dashboard configmaps
#kubectl create configmap sp-dashboard-conf --from-file=${download_location}/status-dashboard/conf -n cellery-system
#kubectl create configmap sp-worker-bin --from-file=sp-worker/bin -n cellery-system
#Create observability portal deployment, service and ingress.
kubectl create configmap observability-portal-config --from-file=${download_location}/mesh-observability-${release_version}/components/global/portal/io.cellery.observability.ui/node-server/config -n cellery-system
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/observability/portal/observability-portal.yaml -n cellery-system

# Create K8s Metrics Config-maps
kubectl create configmap k8s-metrics-prometheus-conf --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/observability/prometheus/config -n cellery-system
kubectl create configmap k8s-metrics-grafana-conf --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/observability/grafana/config -n cellery-system
kubectl create configmap k8s-metrics-grafana-datasources --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/observability/grafana/datasources -n cellery-system
kubectl create configmap k8s-metrics-grafana-dashboards --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/observability/grafana/dashboards -n cellery-system
kubectl create configmap k8s-metrics-grafana-dashboards-default --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/observability/grafana/dashboards/default -n cellery-system

#Create K8s Metrics deployment, service and ingress.
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/observability/prometheus/k8s-metrics-prometheus.yaml -n cellery-system
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/observability/grafana/k8s-metrics-grafana.yaml -n cellery-system
}

function init_control_plane () {
local download_location=$1
local iaas=$2
local release_version=$3

#Setup Celley namespace, create service account and the docker registry credentials
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/system/ns-init.yaml

if [ $iaas == "kubeadm" ] || [ $iaas == "k8s" ]; then
    HOST_NAME=$(hostname | tr '[:upper:]' '[:lower:]')
    #label the node if k8s provider is kubeadm
    kubectl label nodes $HOST_NAME disk=local

fi
#Create credentials for docker.wso2.com
#kubectl create secret docker-registry wso2creds --docker-server=docker.wso2.com --docker-username=$DOCKER_REG_USER \
# --docker-password=$DOCKER_REG_PASSWD --docker-email=$DOCKER_REG_USER_EMAIL -n cellery-system
}

function deploy_istio () {
local download_location=$1
local istio_version=$2
local release_version=$3

#Install Istio
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/system/istio-crds.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/system/istio-demo-cellery.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/system/istio-gateway.yaml

kubectl wait deployment/istio-pilot --for condition=available --timeout=300s -n istio-system
#Enabling Istio injection
kubectl label namespace default istio-injection=enabled
}

function deploy_knative_serving () {
local download_location=$1
local release_version=$2
#Install Knative serving
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/system/knative-serving.yaml
}

function deploy_cellery_crds () {
local download_location=$1
local release_version=$2

#Install Cellery crds
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/controller/01-cluster-role.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/controller/02-service-account.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/controller/03-cluster-role-binding.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/controller/04-crd-cell.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/controller/05-crd-gateway.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/controller/06-crd-token-service.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/controller/07-crd-service.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/controller/08-config.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/controller/09-autoscale-policy.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/controller/10-controller.yaml
}

function deploy_cellery_idp () {
local download_location=$1
local release_version=$2

#Create the IDP config maps
kubectl create configmap identity-server-conf --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-idp/conf -n cellery-system
kubectl create configmap identity-server-conf-datasources --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-idp/conf/datasources/ -n cellery-system
kubectl create configmap identity-server-conf-identity --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-idp/conf/identity -n cellery-system
kubectl create configmap identity-server-tomcat --from-file=${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-idp/conf/tomcat -n cellery-system
#kubectl create configmap identity-server-security --from-file=${download_path}/distribution-${release_version}/installer/k8s-artefacts/global-idp/conf/security -n cellery-system

#Create IDP deployment and the service
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/global-idp/global-idp.yaml -n cellery-system
}

function create_artifact_folder () {
 local tmp_folder=$1

if [ -d $tmp_folder ]; then
    mv $tmp_folder ${tmp_folder}.$(date +%s)
fi

mkdir ${tmp_folder}
}

function download_cellery_artifacts () {
local base_url=$1
local download_path=$2
local yaml_list=("$@")

for file_path in "${yaml_list[@]}"
do
  dir_name=""
  if [[ $file_path =~ / ]]; then
    dir_name=${file_path%/*}
  fi
  wget "$base_url/$file_path" -P "$download_path/$dir_name" -a cellery-setup.log
done
}

function download_extract_celley_k8s_artifacts (){
local download_path=$1
local distribution_url=$2
local file_name=$3
wget ${distribution_url} -O ${download_path}/${file_name} -a cellery-setup.log
unzip ${download_path}/${file_name} -d ${download_path}
}


function install_nginx_ingress_kubeadm () {
local download_location=$1
local $release_version=$2

#Install nginx-ingress for control plane ingress
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/system/mandatory.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/system/service-nodeport.yaml
}

function install_nginx_ingress_gcp () {
local download_location=$1

#Install nginx-ingress for control plane ingress
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/system/mandatory.yaml
kubectl apply -f ${download_location}/distribution-${release_version}/installer/k8s-artefacts/system/cloud-generic.yaml
}

function read_configs_envs () {
if [ -f cellery.env ]; then
    source ./cellery.env
fi

#Initialize the IaaS specific configurations.
download_path=${DOWNLOAD_PATH:-tmp-cellery}
release_version=${RELEASE_VERSION:-master}
git_base_url=${GIT_BASE_URL:-https://github.com/wso2-cellery/distribution}
distribution_url=${GIT_DISTRIBUTION_URL:-https://github.com/wso2-cellery/distribution/archive}
mesh_observability_url=${GIT_MESH_OBSERVABILITY_URL:-https://github.com/wso2-cellery/mesh-observability/archive}
istio_version=${ISTIO_VERSION:-1.0.2}

if [[ -n ${IAAS/[ ]*\n/} ]]; then
    iaas=$IAAS
    if [ $iaas == "kubeadm" ]; then
        k8s_version=${K8S_VERSION:-1.11.3-00}
        flannel_version=${FLANNEL_VERSION:-0.10.0}
    elif [ $iaas == "GCP" ]; then
        k8s_version=${K8S_VERSION:-1.11.8-gke.5}
        #gcp_project=${GCP_PROJECT:-myc-ellery}
        #gcp_compute_region=${GCP_COMPUTE_REGION:-us-west1}
        gcp_compute_zone=${GCP_COMPUTE_ZONE:-us-west1-c}
        gcp_k8s_cluster_name=${GCP_K8S_CLUSTER_NAME:-cellery-k8s}
        #GCP_K8S_CLUSTER_VERSION=latest
        gcp_k8s_cluster_machine_type=${GCP_K8S_CLUSTER_MACHINE_TYPE:-n1-standard-4}
        gcp_k8s_cluster_num_nodes=${GCP_K8S_CLUSTER_NUM_NODES:-1}
        gcp_cluster_min_nodes=${GCP_K8S_CLUSTER_MIN_NODES:-1}
        gcp_k8s_cluster_max_nodes=${GCP_K8S_CLUSTER_MAX_NODES:-3}
        gcp_sql_tire=${GCP_SQL_TIER:-db-n1-standard-1}
    fi
fi
}
#-----------------------------------------------------------------------------------------------------------------------

#Read parameter from the STDIN as bash positional parameters and set IaaS, GCP Project and GCP Compute Zone
if [[ -n ${1/[ ]*\n/} ]]; then
    #iaas=$1
    IAAS=$1
    if [ $IAAS == "GCP" ]; then
        if [[ -n ${2/[ ]*\n/} ]]; then
            gcp_project=$2
        fi
        if [[ -n ${3/[ ]*\n/} ]]; then
            gcp_compute_zone=$3
        fi
    fi
fi

#Read IaaS configurations via environment variables.
read_configs_envs

declare -A config_params
declare -A nfs_config_params

#Create temporary folder to download Cellery artifacts
create_artifact_folder $download_path

echo "🕷️ Downloading Cellery artifacts to ${download_path}"
distribution_artifact_url="${distribution_url}/${release_version}.zip"
mesh_observability_artifact_url="${mesh_observability_url}/${release_version}.zip"
download_extract_celley_k8s_artifacts $download_path $distribution_artifact_url "distribution_${release_version}.zip"
download_extract_celley_k8s_artifacts $download_path $mesh_observability_artifact_url "observability_${release_version}.zip"

#Install K8s
if [[ -n ${iaas/[ ]*\n/} ]]; then
    if [ $iaas == "GCP" ]; then
        echo "ℹ️ Selected k8s provider: GCP"
        echo "ℹ️ GCP Project $gcp_project hosted in $gcp_compute_zone"
        if [ -n $gcp_project ]; then
            install_k8s_gcp $gcp_project \
                            $gcp_compute_zone \
                            $gcp_k8s_cluster_name \
                            $k8s_version \
                            $gcp_k8s_cluster_machine_type \
                            $gcp_cluster_min_nodes \
                            $gcp_k8s_cluster_max_nodes \
                            $gcp_k8s_cluster_num_nodes
        else
            echo "GCP project name is required"
            exit 0
        fi
    elif [ $iaas == "kubeadm" ]; then
        echo "ℹ️ Selected k8s provider: kubeadm"
        install_k8s_kubeadm $k8s_version
        #configure k8s master node
        configure_k8s_kubeadm
    else
        echo "Installation script supported k8s providers are GCP and Kubeadm."
        exit 0
    fi
else
    echo "Installing Cellery into an existing k8s cluster"
    if ! [ -x "$(command -v kubectl)" ]; then
	echo kubectl is not installed.
	exit 1
    fi
    iaas="k8s"
fi

#Create cellery-system namespace and the cellery service account
echo "🔧 Creating cellery-system namespace and the service account"
init_control_plane $download_path $iaas $release_version

#Deploy Cellery Data plane
echo "🔧 Deploying Istio version $istio_version"
deploy_istio $download_path $istio_version $release_version

echo "🔧 Deploying Knative"
deploy_knative_serving $download_path $release_version

echo "🔧 Deploying Cellery CRDs"
deploy_cellery_crds $download_path $release_version


read -p "⛏️ Do you want to deploy Cellery control plane [y/N]: " install_control_plane < /dev/tty

if [ $install_control_plane == "y" ]; then

    #Deploy/Configure NFS APIM artifact
     if [ $iaas == "GCP" ]; then
        read -p "⛏️ Do you want to deploy a NFS server [Y/n]: " install_nfs < /dev/tty
        if [[ -z ${install_nfs/[ ]*\n/} ]]; then
            install_nfs="Y"
        fi

        if [ $install_nfs == "n" ]; then
             read_nfs_connection
        else
            create_nfs_share_gcp "data" $gcp_compute_zone
        fi
        update_apim_nfs_volumes $download_path $release_version
    fi

    #Deploy/configure MySQL / APIM datasources
    read -p "⛏️ Do you want to deploy MySQL server [y/N]: " install_mysql < /dev/tty
    if [[ -z ${install_mysql/[ ]*\n/} ]]; then
            install_mysql="N"
        fi
    if [ $install_mysql == "y" ]; then

        if [ $iaas == "GCP" ]; then
            #Read db user / passwd
            read_control_plane_datasources_configs
            #Update the sql
            update_control_plance_sql $download_path $release_version
            deploy_mysql_server_gcp $download_path \
                                    "cellery-mysql-$((1 + RANDOM % 1000))" \
                                    $gcp_compute_zone \
                                    $gcp_sql_tire \
                                    $release_version
        elif [ $iaas == "kubeadm" ]; then
            read_control_plane_datasources_configs
            #update the sql file
            update_control_plance_sql $download_path $release_version
            deploy_mysql_server $download_path $release_version
        elif [ $iaas == "k8s" ]; then
            echo "🔧 Deploy MySQL server into the existing K8s clusters"
            read_control_plane_datasources_configs
            update_control_plance_sql $download_path $release_version
            deploy_mysql_server $download_path $release_version
        fi
    else
        read_control_plane_datasources_configs
    fi

    update_control_plane_datasources $download_path $release_version

    echo "ℹ️ Start to Deploying the Cellery control plane"
    echo "🔧 Deploying the control plane API Manager"

    deploy_global_gw $download_path $iaas $release_version
    #deploy_global_pubstore $download_path

    echo "🔧Deploying Stream Processor"
    deploy_sp_dashboard_worker $download_path $release_version
else

#Deploy/configure MySQL / IDP datasources
read -p "⛏️ Do you want to deploy MySQL server [y/N]: " install_mysql < /dev/tty
if [[ -z ${install_mysql/[ ]*\n/} ]]; then
        install_mysql="N"
    fi
if [ $install_mysql == "y" ]; then

    if [ $iaas == "GCP" ]; then
        #Read db user / passwd
        read_control_plane_datasources_configs
        #Update the sql
        update_control_plance_sql $download_path
        deploy_mysql_server_gcp $download_path \
                                "cellery-mysql-$((1 + RANDOM % 1000))" \
                                $gcp_compute_zone \
                                $gcp_sql_tire \
                                $release_version
    elif [ $iaas == "kubeadm" ]; then
        read_control_plane_datasources_configs
        #update the sql file
        update_control_plance_sql $download_path $release_version
        deploy_mysql_server $download_path $release_version
    elif [ $iaas == "k8s" ]; then
        echo "🔧 Deploy MySQL server into the existing K8s clusters"
        read_control_plane_datasources_configs
        update_control_plance_sql $download_path $release_version
        deploy_mysql_server $download_path $release_version
    fi
else
    read_control_plane_datasources_configs
fi
update_control_plane_datasources $download_path $release_version
echo "🔧 Deploying Cellery IDP"
deploy_cellery_idp $download_path $release_version
fi

echo "🔧 Deploying ingress-nginx"
if [ $iaas == "kubeadm" ]; then
    install_nginx_ingress_kubeadm $download_path $release_version
elif [ $iaas == "GCP" ]; then
    install_nginx_ingress_gcp $download_path $release_version
fi
echo "ℹ️ Cellery installation is finished."
echo "-=🎉=-"
