# NARRATIV Jenkins pipeline setup

This pipeline implements CI plus GitOps delivery. Jenkins builds and publishes an image, then commits the image tag to [`homelabs-gitops`](https://github.com/rizkysiregar/homelabs-gitops). Argo CD observes that GitOps commit and deploys it to RKE2. Jenkins does not receive a kubeconfig and must never run `kubectl`, Helm, or Argo CD commands.

## What the Jenkins agent needs

The Jenkins agent that runs this job must have:

- a `docker` CLI;
- permission to access the host Docker socket;
- `git` available;
- outbound access to GitHub and GHCR (`ghcr.io`).

The pipeline runs Maven and `yq` inside short-lived Docker containers, so Java, Maven, and `yq` do not need to be installed on the Jenkins host. The Docker host is expected to be AMD64 Linux; the pipeline nevertheless explicitly builds `linux/amd64`.

Verify the Jenkins execution environment before creating the job:

```bash
docker version
docker run --rm hello-world
```

If Jenkins itself runs in Docker, it needs the Docker socket mounted and its runtime user must be allowed to access it. The pipeline uses `--volumes-from "$HOSTNAME"` so its short-lived Maven and `yq` containers see the Jenkins workspace volume; a host-path bind mount would incorrectly create an empty directory on the Docker host. If the Jenkins container has a custom hostname, set its container ID as the `JENKINS_CONTAINER_ID` environment variable in the Jenkins container. Mounting the socket gives that Jenkins instance privileged control over the Docker host, so only trusted repository users should be able to alter this pipeline.

## Required Jenkins credentials

Create the following credentials in **Manage Jenkins → Credentials → System → Global credentials**.

| Credential ID | Type | Value and required access |
| --- | --- | --- |
| `github-all-repo-pat` | Username with password | GitHub username and PAT. It needs repository contents read/write for `homelabs-gitops`, and (classic PAT) the `write:packages` scope to push `ghcr.io/rizkysiregar/narrativ-service`; it also needs read access to `narrativ-backend` if Jenkins checks out the source through HTTPS. |

Do not place the token in a Jenkinsfile, `.env`, GitOps manifest, or shell command history.
## Create the job

1. In Jenkins, select **New Item**.
2. Name it `narrativ-service` and choose **Pipeline**.
3. Under **Pipeline**, choose **Pipeline script from SCM**.
4. Select **Git** as SCM.
5. Set repository URL to `https://github.com/rizkysiregar/narrativ-backend.git`.
6. Select credential `github-all-repo-pat` and branch `*/master`.
7. Leave the script path as `Jenkinsfile`.
8. Under **Build Triggers**, enable the GitHub webhook trigger if the GitHub plugin is installed.
9. Save, then use **Build Now** once as the initial validation.

For GitHub-triggered runs, add a webhook in the backend GitHub repository:

```text
https://jenkins.rizkysiregar.my.id/github-webhook/
```

Select `application/json` and the **Just the push event** event. Configure a webhook secret only if the Jenkins GitHub integration is configured to validate it.

## Pipeline flow

For a commit on `master`, the Jenkinsfile performs this sequence:

1. Checks out the exact backend commit and derives a 12-character Git SHA, for example `a1b2c3d4e5f6`.
2. Runs `mvn clean test` in a Java 25 Maven container. A failed test stops the release.
3. Packages `target/*.jar`.
4. Builds and pushes `ghcr.io/rizkysiregar/narrativ-service:a1b2c3d4e5f6` to GitHub Container Registry.
5. Clones `homelabs-gitops/main`.
6. Updates only the matching `images[].newTag` value in `apps/narrativ-dev/kustomization.yaml` using `yq`.
7. Commits `deploy(narrativ-dev): a1b2c3d4e5f6` and pushes it to `main`.
8. Stops. Argo CD detects the GitOps commit and performs the RKE2 rollout.

The commit SHA is immutable, unlike `latest`. It tells you exactly which backend source commit is running in the cluster and makes rollback a Git revert or a tag change in `kustomization.yaml`.

## First-run checks

After a successful Jenkins build, verify all three layers:

```bash
# 1. GHCR: confirm the SHA tag exists in ghcr.io/rizkysiregar/narrativ-service.
# 2. GitOps: confirm Jenkins committed the newTag value.
git -C homelabs-gitops log -1 --oneline
git -C homelabs-gitops diff HEAD~1 HEAD -- apps/narrativ-dev/kustomization.yaml

# 3. Cluster: Argo CD should be Synced and Healthy, then test the public endpoint.
curl -i https://api-dev-narrativ.rizkysiregar.my.id/api/v1/actuator/health
```

If the GitOps push is rejected because someone pushed to `main` while the pipeline was running, do not force-push. Rerun the pipeline so it starts from the latest desired state.

## Manual rollback

To return to an earlier image, edit only `newTag` in `apps/narrativ-dev/kustomization.yaml` to a previously pushed SHA, commit, and push. Argo CD will reconcile the rollback. Do not use `kubectl set image` for Git-managed NARRATIV resources because Argo CD will overwrite that manual state.
