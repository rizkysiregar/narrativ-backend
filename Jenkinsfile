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


                // skip pipeline if this commit was made by Jenkins itself
                // prevents infinite loop when Jenkins pushes values.yaml back to git
                script {
                    def commitAuthor = sh(
                            script: 'git log -1 --pretty=format:%an',
                            returnStdout: true
                    ).trim()

                    if (commitAuthor == 'Jenkins') {
                        currentBuild.result = 'NOT_BUILT'
                        error("Skipping pipeline — commit made by Jenkins CI")
                    }
                }
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


        // Jenkins only updates git — ArgoCD handles the actual deploy
        stage('Update Helm Values') {
            steps {
                container('helm') {
                    withCredentials([
                            gitUsernamePassword(
                                    credentialsId: 'rizkysiregar-github-pat',
                                    gitToolName: 'Default'
                            )
                    ]) {
                        sh '''
                    apk add --no-cache git

                    cd "$WORKSPACE"

                    # git security feature blocks access to repos owned by different users
                    # since helm container runs as root (user 0) but workspace was created
                    # by jnlp container (different user), we need to explicitly trust this path
                    git config --global --add safe.directory "$WORKSPACE"

                    # update image tag in values.yaml
                    sed -i "s/^  tag:.*/  tag: ''' + env.IMAGE_TAG + '''/" ./helm/feedzmore/values.yaml

                    # update appVersion in Chart.yaml
                    APP_VER="''' + env.APP_VERSION + '''"
                    sed -i "s/^appVersion:.*/appVersion: \\"$APP_VER\\"/" ./helm/feedzmore/Chart.yaml

                    # configure git identity
                    git config user.email "jenkins@feedzmore.ci"
                    git config user.name "Jenkins"

                    # commit and push back to master
                    git add helm/feedzmore/values.yaml helm/feedzmore/Chart.yaml
                    git commit -m "ci: update image tag to ''' + env.IMAGE_TAG + '''"
                    git push origin master
                '''
                    }
                }
            }
        }



    }
}