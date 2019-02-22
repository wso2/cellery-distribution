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
#cat cellery-cleanup.sh | bash -s -- kubeadm

#curl https://raw.githubusercontent.com/cellery-io/distribution/master/installer/scripts/cellery-runtime-deployer/cellery-cleanup.sh | bash -s -- kubeadm

iaas=$1

if [ -z $iaas ]; then
    echo "Please provide the K8s provider. [ GCP | kubeadm ]."
    exit 1
fi

if [ $iaas == "kubeadm" ]; then

    echo "Removing docker kubeadm kubelet kubectl."
    echo
    read -p "⚠️  Do you want to purge kubelet kubectl and docker [y/N]: " deb_remove_option < /dev/tty

    if [ $deb_remove_option == "y" ]; then
        DEL_LEVEL="purge"
    else
        DEL_LEVEL="remove"
    fi

    yes | sudo kubeadm reset

    sudo apt-get $DEL_LEVEL --allow-change-held-packages kubelet kubeadm kubectl docker.io docker-ce

    echo "ℹ️  Removing /mnt/mysql and /mnt/apim_repository_deployment_server."
    echo

    if [ -d /mnt/mysql ]; then
        sudo mv /mnt/mysql "/mnt/mysql.$(date +%s)"
    fi

    if [ -d /mnt/apim_repository_deployment_server ]; then
        sudo mv /mnt/apim_repository_deployment_server "/mnt/apim_repository_deployment_server.$(date +%s)"
    fi
fi

echo "Cellery cleanup is finished❗"
