#!/bin/bash

mvn clean package -DskipTests

tagGC=`docker images | grep vertx-validator |awk -F" " '{if ($1=="gcr.io/tranformacion-it-lab/vertx-validator") {print $3}}'`
if [ "$tagGC" != "" ]
then
	echo "Borrando la imagen anterior "$tagGC
	docker rmi $tagGC -f
fi
echo "Creando la imagen...."
docker build -t vertx-validator .
tag=`docker images | grep vertx-validator |awk -F" " '{if ($1=="vertx-validator") {print $3}}'`
echo "Creando tag imagen: "$tag
docker tag $tag gcr.io/tranformacion-it-lab/vertx-validator:1.0.0
echo "Subiendo a Google Cloud..."
docker push gcr.io/tranformacion-it-lab/vertx-validator:1.0.0
pod=`kubectl get pod | grep vertx-validator | awk -F" " '{print $1}'`
echo "Borrando el POD "$pod
kubectl delete pod $pod
watch kubectl get pod
