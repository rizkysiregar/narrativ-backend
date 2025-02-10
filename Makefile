# Define variables
APP_NAME=feedzmore-service
TAG=latest
# Docker Hub username
DOCKER_REPO=441293

# Build Docker image
build:
	docker build -t $(APP_NAME):$(TAG) .

# Tag the image for Docker Hub
tag:
	docker tag $(APP_NAME):$(TAG) $(DOCKER_REPO)/$(APP_NAME):$(TAG)

# Push the image to Docker Hub
push: tag
	docker push $(DOCKER_REPO)/$(APP_NAME):$(TAG)

# Run the container with Docker Compose
run:
	docker-compose up -d

# Stop and remove containers
stop:
	docker-compose down

# Clean up dangling images
clean:
	docker system prune -f
