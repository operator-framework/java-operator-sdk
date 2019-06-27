#!/usr/bin/env bash

TAG=$(uuidgen)
#IMAGE=adamsandor83/nginx-www-operator:$TAG
IMAGE=nginx-www-operator:$TAG

docker build -t $IMAGE .

#docker push $IMAGE

kubectl set image deployment/nginx-www-operator -n nginx-www-operator operator=$IMAGE