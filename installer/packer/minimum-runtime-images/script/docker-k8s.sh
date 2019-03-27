#!/bin/bash -eu
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

K8S_VERSION=1.11.3-00
node_type=master
UBUNTU_VERSION=$(lsb_release -r | awk ' /'Release'/ {print $2} ')

#Update all installed packages.
apt-get update
#yes | apt-get upgrade

#if you get an error similar to
#'[ERROR Swap]: running with swap on is not supported. Please disable swap', disable swap:
#sudo swapoff -a
sed -i '/ swap / s/^\(.*\)$/#\1/g' /etc/fstab

# install some utils
apt-get install -y apt-transport-https ca-certificates curl software-properties-common

#Install Docker
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -

if [ $UBUNTU_VERSION == "16.04" ]; then
    add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu xenial stable"
elif [ $UBUNTU_VERSION == "18.04" ]; then
    add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu bionic stable"
else
    #default tested version
    add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu xenial stable"
fi
apt-get update
apt-get install -y docker-ce=18.06.0~ce~3-0~ubuntu

#Install unzip
apt-get install -y unzip

#Enable docker service
systemctl enable docker.service

#Update the apt source list
curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
add-apt-repository "deb [arch=amd64] http://apt.kubernetes.io/ kubernetes-xenial main"

#Install K8s components
apt-get update
apt-get install -y kubelet=$K8S_VERSION kubeadm=$K8S_VERSION kubectl=$K8S_VERSION kubernetes-cni=0.6.0-00

apt-mark hold kubelet kubeadm kubectl

#Initialize the k8s cluster
kubeadm init --pod-network-cidr=10.244.0.0/16

sleep 60

#Create .kube file if it does not exists
mkdir -p $HOME/.kube

#Move Kubernetes config file if it exists
if [ -f $HOME/.kube/config ]; then
    mv $HOME/.kube/config $HOME/.kube/config.back
fi

cp -f /etc/kubernetes/admin.conf $HOME/.kube/config
chown -R vagrant:vagrant $HOME/.kube

#if you are using a single node which acts as both a master and a worker
#untaint the node so that pods will get scheduled:
kubectl taint nodes --all node-role.kubernetes.io/master-

#Install Flannel network
kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/v0.10.0/Documentation/kube-flannel.yml

echo "Done."
