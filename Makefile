# Define variables
APP_NAME=feedzmore-service
TAG=latest
# Docker Hub username
DOCKER_REPO=rizkysiregar

# Build Docker image
build:
	docker build -t $(APP_NAME):$(TAG) .

# Remove old latest tag if it exists
rmi-latest:
	-docker rmi -f $(DOCKER_REPO)/$(APP_NAME):$(TAG)

# Tag the image for Docker Hub
tag:
	docker tag $(APP_NAME):$(TAG) $(DOCKER_REPO)/$(APP_NAME):$(TAG)

# Push the image to Docker Hub
push: tag
	docker push $(DOCKER_REPO)/$(APP_NAME):$(TAG)

# Full update: Remove old image, build, tag, and push
update-latest: rmi-latest build tag push

# Run the container with Docker Compose
run:
	docker-compose up -d

# Stop and remove containers
stop:
	docker-compose down

# Clean up dangling images
clean:
	docker system prune -f


sonar-verify:
	 mvn clean verify sonar:sonar \
      -Dsonar.host.url=http://192.168.1.50:9001 \
      -Dsonar.login=squ_cb8ca84be08e1ef1cf41db8a19338108a7d34c9a
