```markdown
# Feedzmore Backend App

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://your-jenkins-url)
[![Quality Gate Status](https://img.shields.io/badge/sonar-quality--gate-blue)](https://your-sonar-url)

Feedzmore is a robust backend application built with **Java 21**, **Spring Boot**, and **Jakarta EE**. It provides a
scalable architecture for [insert brief description of what the app does].

---

## 🚀 Technologies

- **Language:** Java 21
- **Framework:** Spring Boot / Spring Data JPA
- **API Specification:** Jakarta EE
- **Database:** [e.g., PostgreSQL / MySQL]
- **Automation:** Maven, Jenkins, Docker
- **Quality Assurance:** SonarQube, JUnit

## 🛠 Prerequisites

Before running the application, ensure you have the following installed:

- **JDK 21**
- **Maven 3.9+**
- **Docker** (only to build the image)

## 🏗️ Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/feedzmore.git
cd feedzmore
```

```


### 2. Environment Setup
Copy `.env.example` to `.env` and fill in the values. Infrastructure (PostgreSQL, Redis, RabbitMQ, MinIO) is the shared development infrastructure; the app is deployed to Kubernetes through GitOps.

### 3. Running the App

You can use the Maven wrapper to run the application:

```shell script
./mvnw spring-boot:run
```

## 🧪 Development Workflow

To maintain code quality, please follow these guidelines:

### Pre-push Check

Always ensure tests pass before pushing your code:

```shell script
mvn test
```

### Pre-merge Quality Gate

Before merging into the main branch, run a full verification and Sonar analysis:

```shell script
mvn clean verify sonar:sonar
```

or 
````shell script
SONAR_TOKEN=your-token mvn clean verify sonar:sonar \
  -Dsonar.host.url=http://192.168.1.50:9001 \
  -Dsonar.token=$SONAR_TOKEN
````

## 📦 Deployment

### Docker

Build the application image:

```shell script
docker build -t feedzmore-backend .
```

### CI/CD

This project includes a `Jenkinsfile` for automated pipelines. The pipeline handles:

1. Running tests and packaging the JAR with Java 25.
2. Building and pushing an immutable AMD64 image to Docker Hub.
3. Updating the image tag in `homelabs-gitops`.
4. Leaving deployment to Argo CD; Jenkins has no Kubernetes credentials.

## 📄 Project Structure

- `src/`: Application source code.
- `Makefile`: Useful shortcuts for common tasks.
- `Jenkinsfile`: Pipeline definitions.

---
© 2024 Feedzmore Team
