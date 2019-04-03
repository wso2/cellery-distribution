#!/bin/bash

packer build -var 'version=0.2.0' -on-error=ask ubuntu.json 
