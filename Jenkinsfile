pipeline {
    agent {
        kubernetes {
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    image: maven:3.9-eclipse-temurin-25
    command: ['sleep']
    args: ['99d']
  - name: buildah
    image: quay.io/buildah/stable:latest
    workingDir: /home/jenkins/agent
    securityContext:
      privileged: true
    command: ['sleep']
    args: ['99d']
    volumeMounts:
    - name: varlibcontainers
      mountPath: /var/lib/containers
  - name: kubectl
    image: bitnami/kubectl:latest
    command: ['sleep']
    args: ['99d']
    workingDir: /home/jenkins/agent
    securityContext:
      runAsUser: 1000
  volumes:
  - name: varlibcontainers
    emptyDir: {}
"""
        }
    }

    environment {
        IMAGE_NAME = "rizkysiregar/feedzmore-service"
        IMAGE_TAG = "${env.GIT_COMMIT.take(7)}"
        DOCKER_CREDS = credentials('rizkysiregar-dockerhub-pat')
    }

    stages {
        stage('Checkout Code') {
            steps {
                git branch: 'master',
                    credentialsId: 'rizkysiregar-github-pat',
                    url: 'https://github.com/rizkysiregar/feedzmore-backend.git'
            }
        }

        stage('Maven Build') {
            steps {
                container('maven') {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Build & Push Image') {
            steps {
                container('buildah') {
                    sh """
                        # Tell Buildah to use VFS driver to avoid kernel permission issues
                        export BUILDAH_ISOLATION=chroot

                        # 1. Login
                        echo \$DOCKER_CREDS_PSW | buildah --storage-driver vfs login -u \$DOCKER_CREDS_USR --password-stdin docker.io

                        # 2. Build with VFS driver
                        buildah --storage-driver vfs bud -t $IMAGE_NAME:$IMAGE_TAG -t $IMAGE_NAME:latest .

                        # 3. Push with VFS driver
                        buildah --storage-driver vfs push $IMAGE_NAME:$IMAGE_TAG
                        buildah --storage-driver vfs push $IMAGE_NAME:latest
                    """
                }
            }
        }

        stage('Deploy to K3s') {
            steps {
                container('kubectl') {
                    sh """
                        kubectl set image deployment/feedzmore-app feedzmore-app=$IMAGE_NAME:$IMAGE_TAG -n app
                        kubectl rollout status deployment/feedzmore-app -n app
                    """
                }
            }
        }
    }
}
