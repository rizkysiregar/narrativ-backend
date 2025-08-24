pipeline {
    agent any

    tools {
        maven 'Maven3' // name you set in Global Tool Config
    }

    environment {
        GIT_CREDENTIALS = 'rizkysiregar-github-pat'
        DOCKER_HUB_CREDENTIALS = 'rizkysiregar-dockerhub-pat'
        SSH_CREDENTIALS = 'rizkysiregar-ubuntu-docker-host-pat'
        IMAGE_NAME = "rizkysiregar/feedzmore-service"
//         IMAGE_TAG = "${env.BUILD_NUMBER}"
        IMAGE_TAG = "${env.GIT_COMMIT.take(7)}"

        SERVER_HOST = "192.168.1.50"
    }

    stages {
        stage('Checkout Code') {
            steps {
                git branch: 'master', credentialsId: "${GIT_CREDENTIALS}", url: 'https://github.com/rizkysiregar/feedzmore-backend.git'
            }
        }

        stage('Build & Test') {
                   steps {
                       sh 'mvn clean package -DskipTests'
                   }
        }

        stage('Build Docker Image') {
            steps {
                sh "docker build -t $IMAGE_NAME:$IMAGE_TAG -t $IMAGE_NAME:latest ."
            }
        }

        stage('Push Docker Image') {
            steps {
                withDockerRegistry([credentialsId: "${DOCKER_HUB_CREDENTIALS}", url: '']) {
                    sh "docker push $IMAGE_NAME:$IMAGE_TAG"
                    sh "docker push $IMAGE_NAME:latest"
                }
            }
        }

      stage('Deploy') {
          steps {
              sh '/home/rizkysiregar/feedzmore/deploy.sh'
          }
      }


// if want to use ssh into server, but since jenkins and feedzmore are on the same host so just directly run docker command here
//         stage('Deploy on Server') {
//             steps {
//                 sshagent (credentials: ["${SSH_CREDENTIALS}"]) {
//                     sh """
//                         ssh -o StrictHostKeyChecking=no rizkysiregar@${SERVER_HOST} '
//                             cd /home/rizkysiregar/feedzmore &&
//                             docker compose pull &&
//                             docker compose up -d --force-recreate &&
//                             docker image prune -af
//                         '
//                     """
//                 }
//             }
//         }




    }

    post {
        success {
            echo "Deployment successful: $IMAGE_NAME:$IMAGE_TAG"
        }
        failure {
            echo "Deployment failed!"
        }
    }
}
