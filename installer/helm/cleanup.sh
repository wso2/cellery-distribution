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

helm del --purge cellery-runtime

read -p "Do you want to delete ingress controller (nginx ingress) [y/N]: " delete_nginx_ingress < /dev/tty
    if [ -z ${delete_nginx_ingress/[ ]*\n/} ] || [ ${delete_nginx_ingress} == "N" ]; then
        echo "Cellery runtime cleanup is finished"
        exit 0
    elif [[ ${delete_nginx_ingress} == "y" ]]; then
        helm del --purge ingress-controller
        echo "Cellery runtime cleanup is finished"
    fi
