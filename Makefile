APP_NAME ?= narrativ-service
DOCKER_REPO ?= rizkysiregar
TAG ?= latest
PLATFORM ?= linux/amd64
IMAGE := $(DOCKER_REPO)/$(APP_NAME):$(TAG)
MAVEN ?= ./mvnw

.PHONY: package image-build image-push build push update-latest clean sonar-verify

# Builds the executable JAR expected by the Dockerfile.
package:
	$(MAVEN) clean package -DskipTests

# Builds a Linux/AMD64 image and loads it into the local Docker daemon.
# Use this target to inspect or run the image locally after cross-compilation.
image-build: package
	docker buildx build --platform $(PLATFORM) --load --tag $(IMAGE) .

# Builds and pushes a Linux/AMD64 image directly to Docker Hub.
# Docker Buildx handles ARM macOS to AMD64 Linux emulation.
image-push: package
	docker buildx build --platform $(PLATFORM) --push --tag $(IMAGE) .

# Backward-compatible aliases.
build: image-build
push: image-push
update-latest: image-push

clean:
	docker system prune -f

# Usage: SONAR_TOKEN=... make sonar-verify
sonar-verify:
	@test -n "$(SONAR_TOKEN)" || (echo "SONAR_TOKEN is required" && exit 1)
	$(MAVEN) clean verify sonar:sonar \
		-Dsonar.host.url=http://192.168.1.50:9001 \
		-Dsonar.token=$(SONAR_TOKEN)
