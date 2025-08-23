pipeline {
    agent any

    environment {
        GIT_CREDENTIALS = 'rizkysiregar-github-pat'
        DOCKER_HUB_CREDENTIALS = 'rizkysiregar-dockerhub-pat'
        SSH_CREDENTIALS = 'rizkysiregar-ubuntu-docker-host-pat'
        IMAGE_NAME = "rizkysiregar/feedzmore-service"
        SERVER_HOST = "192.168.1.50"
    }

    stages {
        stage('Checkout Code') {
            steps {
                git branch: 'master', credentialsId: "${GIT_CREDENTIALS}", url: 'https://github.com/rizkysiregar/feedzmore-backend.git'
            }
        }

        stage('Build Docker Image') {
            steps {
                // Using 'latest', but you can also use BUILD_NUMBER if you want versioned tags
                sh "docker build -t $IMAGE_NAME:latest ."
            }
        }

        stage('Push Docker Image') {
            steps {
                withDockerRegistry([credentialsId: "${DOCKER_HUB_CREDENTIALS}", url: '']) {
                    sh "docker push $IMAGE_NAME:latest"
                }
            }
        }

        stage('Deploy on Server') {
            steps {
                sshagent (credentials: ["${SSH_CREDENTIALS}"]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no rizkysiregar@${SERVER_HOST} '
                            cd /home/rizkysiregar/feedzmore &&
                            docker compose pull &&
                            docker compose up -d --force-recreate
                        '
                    """
                }
            }
        }
    }
}
