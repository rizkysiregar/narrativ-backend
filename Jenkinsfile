pipeline {
    // ─────────────────────────────────────────────────────────────
    // AGENT: defines WHERE and HOW the pipeline runs.
    // Instead of running on Jenkins directly, we spin up a
    // temporary Kubernetes Pod with multiple containers inside it.
    // Each container is a specialized tool we need for one stage.
    // The pod is automatically deleted when the pipeline finishes.
    // ─────────────────────────────────────────────────────────────
    agent {
        kubernetes {
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:

  # MAVEN CONTAINER
  # Used for: compiling Java code and building the .jar file
  # 'sleep 99d' keeps the container alive so Jenkins can exec into it
  - name: maven
    image: maven:3.9-eclipse-temurin-25
    command: ['sleep']
    args: ['99d']

  # BUILDAH CONTAINER
  # Used for: building Docker images and pushing them to DockerHub
  # Needs 'privileged: true' because building images requires
  # low-level Linux capabilities (like mounting filesystems)
  - name: buildah
    image: quay.io/buildah/stable:latest
    workingDir: /home/jenkins/agent
    securityContext:
      privileged: true
    command: ['sleep']
    args: ['99d']
    volumeMounts:
    - name: varlibcontainers
      mountPath: /var/lib/containers  # where buildah stores image layers temporarily

  # HELM CONTAINER
  # Used for: updating Chart.yaml and deploying to Kubernetes via Helm
  # runAsUser: 0 means run as root — helm needs this to write cache/config files
  - name: helm
    image: alpine/helm:latest
    command: ['sleep']
    args: ['99d']
    workingDir: /home/jenkins/agent
    securityContext:
      runAsUser: 0

  volumes:
  # Temporary storage for buildah image layers during the build.
  # emptyDir means it's wiped clean when the pod is deleted.
  - name: varlibcontainers
    emptyDir: {}
"""
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ENVIRONMENT: global variables available to all stages.
    // These are evaluated once at the start of the pipeline run.
    // ─────────────────────────────────────────────────────────────
    environment {
        // Docker image name on DockerHub (username/repo-name)
        IMAGE_NAME = "rizkysiregar/feedzmore-service"

        // Short 7-character git commit hash (e.g. "4ed1fe2")
        // Used as the image tag so each build is uniquely identifiable
        // GIT_COMMIT is automatically provided by Jenkins
        IMAGE_TAG = "${env.GIT_COMMIT.take(7)}"

        // Version label for this release:
        // - If triggered by a git tag push → uses the tag name (e.g. "v1.0.0")
        // - If triggered by a regular commit → falls back to commit hash (e.g. "4ed1fe2")
        // TAG_NAME is automatically set by Jenkins when a tag triggers the build
        APP_VERSION = "${env.TAG_NAME ?: env.GIT_COMMIT.take(7)}"

        // DockerHub credentials stored securely in Jenkins credential store.
        // Jenkins automatically creates two variables from this:
        // DOCKER_CREDS_USR → username
        // DOCKER_CREDS_PSW → password/token
        DOCKER_CREDS = credentials('rizkysiregar-dockerhub-pat')
    }

    stages {

        // ─────────────────────────────────────────────────────────
        // STAGE 1: CHECKOUT CODE
        // Pulls your source code from GitHub into the Jenkins
        // workspace so all subsequent stages can access the files.
        // This is what makes ./helm/feedzmore/ available later.
        // ─────────────────────────────────────────────────────────
        stage('Checkout Code') {
            steps {
                git branch: 'master',
                        credentialsId: 'rizkysiregar-github-pat',   // GitHub PAT stored in Jenkins
                        url: 'https://github.com/rizkysiregar/feedzmore-backend.git'
            }
        }

        // ─────────────────────────────────────────────────────────
        // STAGE 2: MAVEN BUILD
        // Compiles the Java source code and packages it into a
        // .jar file inside the target/ folder.
        // -DskipTests skips unit tests to keep the build faster.
        // The .jar file is what gets copied into the Docker image
        // later via your Dockerfile.
        // ─────────────────────────────────────────────────────────
        stage('Maven Build') {
            steps {
                container('maven') {    // switch into the maven container
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        // ─────────────────────────────────────────────────────────
        // STAGE 3: BUILD & PUSH IMAGE
        // Builds a Docker image from your Dockerfile and pushes
        // it to DockerHub with two tags:
        // 1. IMAGE_TAG  → specific tag (e.g. rizkysiregar/feedzmore-service:4ed1fe2)
        // 2. latest     → always points to the most recent build
        // Uses Buildah instead of Docker because Docker-in-Docker
        // has security issues in Kubernetes environments.
        // ─────────────────────────────────────────────────────────
        stage('Build & Push Image') {
            steps {
                container('buildah') {    // switch into the buildah container
                    sh """
                        # Use chroot isolation instead of default linux namespace isolation
                        # Required because we don't have full kernel privileges in Kubernetes
                        export BUILDAH_ISOLATION=chroot

                        # Login to DockerHub using credentials from Jenkins
                        # We pipe the password via stdin to avoid it appearing in logs
                        echo \$DOCKER_CREDS_PSW | buildah --storage-driver vfs login -u \$DOCKER_CREDS_USR --password-stdin docker.io

                        # Build the image from Dockerfile in current directory (.)
                        # -t flag sets the tag, we set two tags in one command
                        buildah --storage-driver vfs bud -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:latest .

                        # Push both tags to DockerHub
                        buildah --storage-driver vfs push ${IMAGE_NAME}:${IMAGE_TAG}
                        buildah --storage-driver vfs push ${IMAGE_NAME}:latest
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────
        // STAGE 4: UPDATE CHART VERSION
        // Updates the appVersion field inside Chart.yaml to reflect
        // the current build version before deploying.
        // - On regular commit: appVersion becomes the commit hash
        // - On tag push:       appVersion becomes the tag (v1.0.0)
        // This is done with sed (stream editor) which finds and
        // replaces a line in the file directly.
        // ─────────────────────────────────────────────────────────
        stage('Update Chart Version') {
            steps {
                container('helm') {    // switch into the helm container
                    sh '''
                        APP_VER="''' + env.APP_VERSION + '''"

                        # Find the line starting with "appVersion:" and replace it
                        # with the current APP_VERSION value
                        sed -i "s/^appVersion:.*/appVersion: \\"$APP_VER\\"/" ./helm/feedzmore/Chart.yaml

                        echo "Chart updated - appVersion: $APP_VER"

                        # Print Chart.yaml so we can verify it in the Jenkins log
                        cat ./helm/feedzmore/Chart.yaml
                    '''
                }
            }
        }

        // ─────────────────────────────────────────────────────────
        // STAGE 5: DEPLOY TO K3S
        // Uses Helm to deploy the app to your Kubernetes cluster.
        // 'helm upgrade --install' means:
        //   - if release doesn't exist → install it fresh
        //   - if release exists        → upgrade it
        // Secrets are injected at deploy time from Jenkins credentials
        // so they are never stored in the repo or Chart files.
        // --rollback-on-failure automatically reverts to the last
        // working revision if this deploy fails.
        // ─────────────────────────────────────────────────────────
        stage('Deploy to K3s') {
            steps {
                container('helm') {    // switch into the helm container
                    // Pull secrets from Jenkins credential store into
                    // temporary shell variables for this step only.
                    // They are masked as **** in the Jenkins logs.
                    withCredentials([
                            string(credentialsId: 'feedzmore-postgres-user', variable: 'POSTGRES_USER'),
                            string(credentialsId: 'feedzmore-postgres-password', variable: 'POSTGRES_PASSWORD'),
                            string(credentialsId: 'feedzmore-minio-access-key', variable: 'MINIO_ACCESS_KEY'),
                            string(credentialsId: 'feedzmore-minio-secret-key', variable: 'MINIO_SECRET_KEY'),
                            string(credentialsId: 'feedzmore-jwt-secret', variable: 'JWT_SECRET'),
                    ]) {
                        // Single quotes sh('') prevents Groovy from interpolating $VARIABLES
                        // so secrets stay protected — the shell handles them instead of Jenkins.
                        // env.IMAGE_TAG and env.APP_VERSION are safely concatenated outside
                        // the single quote block since they are not sensitive values.
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
                                --timeout 300s   # max time to wait for pods to be ready
                        '''
                    }
                }
            }
        }
    }
}