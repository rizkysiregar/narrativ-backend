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
  - name: helm
    image: alpine/helm:latest
    command: ['sleep']
    args: ['99d']
    workingDir: /home/jenkins/agent
    securityContext:
      runAsUser: 0
  volumes:
  - name: varlibcontainers
    emptyDir: {}
"""
        }
    }

    environment {
        IMAGE_NAME = "rizkysiregar/feedzmore-service"
        IMAGE_TAG = "${env.GIT_COMMIT.take(7)}"
        APP_VERSION = "${env.TAG_NAME ?: env.GIT_COMMIT.take(7)}"
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
                        export BUILDAH_ISOLATION=chroot
                        echo \$DOCKER_CREDS_PSW | buildah --storage-driver vfs login -u \$DOCKER_CREDS_USR --password-stdin docker.io
                        buildah --storage-driver vfs bud -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:latest .
                        buildah --storage-driver vfs push ${IMAGE_NAME}:${IMAGE_TAG}
                        buildah --storage-driver vfs push ${IMAGE_NAME}:latest
                    """
                }
            }
        }

        stage('Update Chart Version') {
            steps {
                container('helm') {
                    sh '''
                APP_VER="''' + env.APP_VERSION + '''"
                sed -i "s/^appVersion:.*/appVersion: \\"$APP_VER\\"/" ./helm/feedzmore/Chart.yaml
                echo "Chart updated - appVersion: $APP_VER"
                cat ./helm/feedzmore/Chart.yaml
            '''
                }
            }
        }

        stage('Deploy to K3s') {
            steps {
                container('helm') {
                    withCredentials([
                            string(credentialsId: 'feedzmore-postgres-user', variable: 'POSTGRES_USER'),
                            string(credentialsId: 'feedzmore-postgres-password', variable: 'POSTGRES_PASSWORD'),
                            string(credentialsId: 'feedzmore-minio-access-key', variable: 'MINIO_ACCESS_KEY'),
                            string(credentialsId: 'feedzmore-minio-secret-key', variable: 'MINIO_SECRET_KEY'),
                            string(credentialsId: 'feedzmore-jwt-secret', variable: 'JWT_SECRET'),
                    ]) {
                        sh '''
                            helm upgrade --install feedzmore ./helm/feedzmore \
                                --namespace app \
                                --set image.tag="''' + env.IMAGE_TAG + '''" \
                                --set secrets.POSTGRES_USER="$POSTGRES_USER" \
                                --set secrets.POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
                                --set secrets.MINIO_ACCESS_KEY="$MINIO_ACCESS_KEY" \
                                --set secrets.MINIO_SECRET_KEY="$MINIO_SECRET_KEY" \
                                --set secrets.JWT_SECRET="$JWT_SECRET" \
                                --description "Build #${BUILD_NUMBER} - ''' + env.APP_VERSION + '''" \
                                --rollback-on-failure \
                                --timeout 300s
                        '''
                    }
                }
            }
        }
    }
}