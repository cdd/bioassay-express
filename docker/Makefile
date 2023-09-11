build:
	docker build -f Dockerfile . --tag bae:1.0

hardbuild:
	DOCKER_BUILDKIT=0 docker build --no-cache -f Dockerfile . --tag bae:1.0

run:
	docker run --publish 8088:8080 --add-host host.docker.internal:host-gateway --name bae bae:1.0 &

start: build run

stop:
	docker rm bae --force

restart: stop start

sh:
	docker run -it --rm bae:1.0 /bin/bash

delete:
	docker image rm bae

prune:
	docker image prune -a

pkg:
	cd .. ; ant pkg; ./baeconsole deploy pkg/BioAssayExpress.war docker/BioAssayExpressFullDB.war docker/cfg




