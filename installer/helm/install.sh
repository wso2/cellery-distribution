#!/usr/bin/env bash

# Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

helm_version=$(helm version)
if [[ ! ${helm_version}=~Client ]]; then
    echo "Helm is not installed."
    exit 0
fi

echo "Installing Cellery runtime"
# Deploy Tiller
helm init --wait
# Create service account.
kubectl apply -f helm-service-account.yaml
# Install Cellery runtime.
helm install --name cellery-runtime cellery-runtime

read -p "Do you want to install ingress controller (nginx ingress) [Y/n]: " install_nginx_ingress < /dev/tty
    if [ -z ${install_nginx_ingress/[ ]*\n/} ] || [ ${install_nginx_ingress} == "Y" ]; then
        cd ingress-controller/charts
        helm fetch stable/nginx-ingress
        cd ../..
        helm install --name ingress-controller ingress-controller --namespace ingress-controller
        echo "Cellery runtime installation with ingress controller is finished"
    elif [[ ${install_nginx_ingress} == "n" ]]; then
        echo "Cellery runtime installation is finished"
    fi
