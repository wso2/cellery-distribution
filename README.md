Distribution
============
  [![Build Status](https://wso2.org/jenkins/view/cellery/job/cellery/job/distribution/badge/icon)](https://wso2.org/jenkins/view/cellery/job/cellery/job/distribution/)
  [![GitHub (pre-)release](https://img.shields.io/github/release/cellery-io/distribution/all.svg)](https://github.com/cellery-io/distribution/releases)
  [![GitHub (Pre-)Release Date](https://img.shields.io/github/release-date-pre/cellery-io/distribution.svg)](https://github.com/cellery-io/distribution/releases)
  [![GitHub last commit](https://img.shields.io/github/last-commit/cellery-io/distribution.svg)](https://github.com/cellery-io/distribution/commits/master)
  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Cellery distribution repository contains all the materials required to build cellery runtime. This includes all the kubernetes artifacts generated from other repositories as well. Also, this repository contains source and dockerfiles that's being used to initialize and run the Cell Gateway.

## Setting up Cellery runtime
If you simply want to deploy Cellery runtime, using Cellery SDK would be easier approach for you. However if you you are looking for advanced configerations you can continue with this repositary.
You can use [cellery-deploy.sh](https://github.com/cellery-io/distribution/blob/master/installer/scripts/cellery-runtime-deployer/cellery-deploy.sh) to install Cellery runtime in a Linux box or in Google Cloud GKE.

### Deploy Cellery with kubeadm K8s provider

You can setup the distribution in the kubernetes setup. If you trying out in the developer environment, then you can also install the kubernetes with kubeadm locally by following below steps.

1. Run `swapoff -a` this will immediately disable swap
    
2. Remove any swap entry from `/etc/fstab`

```bash    
$ curl <cellery-deploy script url> | bash -s -- <k8s provider>
```

```bash
$ curl https://raw.githubusercontent.com/cellery-io/distribution/v0.1.0/installer/scripts/cellery-runtime-deployer/cellery-deploy.sh | bash -s -- kubeadm
```

### Google Cloud Platform
If you want to deploy Cellery runtime in Google Cloud Platform you can run the following script.
  

```bash
$ curl <cellery deploy script url> | bash -s -- <K8s provider> <GCP Project ID> <GCP Compute Zone>
```

```bash
$ curl https://raw.githubusercontent.com/cellery-io/distribution/v0.1.0/installer/scripts/cellery-runtime-deployer/cellery-deploy.sh | bash -s -- <K8s provider> <GCP Project ID> <GCP Compute>
```
## Cleaning up Cellery runtime

Simply run following command to cleanup local kubeadm cluster

```bash
$ cat cellery-cleanup.sh | bash -s -- kubeadm
```
  
  

## Repo Structure  

```
├── components
│   ├── gateway
│   │   └── io.cellery.cell.gateway.initializer (Cell gateway & Cell Gateway initializer)  
├── docker
│   ├── global-apim
│   ├── microgateway
│   │   ├── init-container
│   │   ├── init-container-base
│   │   └── microgateway-container
└── installer
    ├── k8s-artefacts (K8s artifacts of the runtime)
    └── scripts
        └── cellery-runtime-deployer
```


## Contribute to Cellery Distribution

The Cellery Team is pleased to welcome all contributors willing to join with us in our journey.

### Build from Source

#### Prerequisites

To get started with building Cellery Distribution, the following are required.

-   Docker
-   Git
-   JDK 1.8+ or higher
-   Maven
-   GNU Make 4.1+

#### Steps to build

This repository only contains source of cell gateway for now..You can build cell gateway using Maven and you can build Docker images manually as well. However, in the same way all our repositories are structured, a proper build can be triggered through Make.

To get started, first clone the repository. If you wish to build for a particular tag, please checkout the required tag. Then run the following command from the root of the project.

```
make all
```

This will build all the modules in the repository and docker images using the build artifacts. Afterwards, if you wish to push the docker images to the repository, run the following command.

**Note**: You need to login to Docker before running this command.

```
make docker-push
```

### Issue Management

Cellery Distribution issue management is mainly handled through GitHub Issues. Please feel free to open an issue about any question, bug report or feature request that you have in mind. (If you are unclear about where your issue should belong to, you can create it in [Cellery SDK](https://github.com/cellery-io/sdk).)

We also welcome any external contributors who are willing to contribute. You can join a conversation in any existing issue and even send PRs to contribute. However, we suggest to start by joining into the conversations and learning about Cellery Distribution as the first step.

Each issue we track has a variety of metadata which you can select with labels:

* **Type**: This represents the kind of the reported issues such as Bug, New Feature, Improvement, etc. 
* **Priority**: This represents the importance of the issue, and it can be scaled from High to Normal.
* **Severity**: This represents the impact of the issue in your current system. If the issue is blocking your system, and it’s having an catastrophic effect, then you can mark is ‘Blocker’. The ‘Blocker’ issues are given high priority as well when we are resolving the issues.

Apart from the information provided above, the issue template added to the repository will guide you to describe the issue in detail so that we can analyze and work on the resolution towards it. We would appreciate if you could fill most of the fields as possible when you are creating the issue. We will evaluate issues based on the provided details and labels, and will allocate them to the Milestones.
