# How to install Cellery using cellery-deploy.sh script.

Cellery all in one setup script helps to deploy Cellery into different providers such as vanilla k8s, GCP and OpenShift. User needs to pass the desired k8s provider to the script as 
given in the example.

If the user selects kubeadm as the k8s provider, installer script will deploy k8s on the Linux. When GCP k8s provider selected, the script creates a k8s cluster, MySQL instance and a NFS 
file share in the GCP platform. 


### Deploy Cellery with kubeadm K8s provider.

```
curl <cellery-deploy script url> | bash -s -- <k8s provider>

```

```
curl https://raw.githubusercontent.com/cellery-io/distribution/master/installer/scripts/cellery-runtime-deployer/cellery-deploy.sh | bash -s -- kubeadm

```

### Deploy Cellery into GCP K8s provider.

```
curl <cellery deploy script url> | bash -s -- <K8s provider> <GCP Project ID> <GCP Compute Zone> 
```

```
curl https://raw.githubusercontent.com/cellery-io/distribution/master/installer/scripts/cellery-runtime-deployer/cellery-deploy.sh | bash -s -- GCP proj-cellery us-west1-c
```

### Deploy Cellery into vanilla K8s provider.
```
curl <cellery-deploy script url> | bash
```

```
curl https://raw.githubusercontent.com/cellery-io/distribution/master/installer/scripts/cellery-runtime-deployer/cellery-deploy.sh | bash
```