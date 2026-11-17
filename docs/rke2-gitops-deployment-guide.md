# NARRATIV dev deployment on RKE2 with GitOps

This guide deploys the NARRATIV backend to the RKE2 development cluster without giving Jenkins access to Kubernetes. It uses Argo CD for deployment reconciliation, `ingress-nginx` for HTTP routing, and Cloudflare Tunnel for public access.

## How to use this guide

This is a one-time setup guide followed by a repeatable release workflow. Complete the steps in the order below. Each step has a **why**, an expected result, and a checkpoint; do not continue if its checkpoint is not satisfied.

| Order | Action | Why | Completion evidence |
| --- | --- | --- | --- |
| 1 | Prepare application configuration | The container must use safe production-like defaults before it starts in the cluster | `application-dev.yml` uses `ddl-auto: validate` |
| 2 | Create the private Kubernetes Secret | Pods need credentials, but Git must not contain them | `narrativ-secrets` exists with all required keys |
| 3 | Create GitOps manifests | Argo CD needs a desired state to reconcile | `kubectl kustomize apps/narrativ-dev` renders valid YAML |
| 4 | Push GitOps manifests | Argo CD only sees committed Git state | Changes are on `homelabs-gitops/main` |
| 5 | Create the Argo CD Application | This tells Argo CD which repository path it owns | Application is `Synced` and `Healthy` |
| 6 | Configure Cloudflare Tunnel hostname | It makes the private ingress reachable through the public domain | Public health endpoint responds |
| 7 | Add Jenkins automation | Later releases update an immutable image tag consistently | Jenkins creates a GitOps image-tag commit |

## 1. Target architecture

```text
Developer pushes code to narrativ-backend/master
        |
        v
Jenkins builds, tests, publishes image to GHCR,
and commits the image tag to homelabs-gitops/main
        |
        v
Argo CD watches homelabs-gitops/apps/narrativ-dev
and applies Kubernetes manifests to RKE2
```

```text
Client
  → https://api-dev-narrativ.rizkysiregar.my.id
  → Cloudflare edge
  → Cloudflare Tunnel on infra VM 192.168.1.50
  → ingress-nginx NodePort on 192.168.1.71:30080
  → NARRATIV Ingress and ClusterIP Service
  → NARRATIV Pod on port 9091
```

Application dependencies remain outside Kubernetes on the development infrastructure VM `192.168.1.50`: PostgreSQL, Redis, RabbitMQ, Zipkin, and MinIO.

## 2. Why the application is not exposed on port 9091

NARRATIV listens on port `9091` **inside its Pod**. A container port is not automatically available as a port on a Kubernetes node.

The public request path is:

```text
Cloudflare Tunnel → NodePort 30080 → Ingress → ClusterIP Service port 80 → Pod port 9091
```

This avoids exposing one host port for each application and lets a single ingress controller route multiple hostnames.

## 3. Existing cluster facts

The RKE2 cluster already has the required ingress controller:

| Item | Value |
| --- | --- |
| Ingress class | `nginx` |
| Controller namespace | `kube-system` |
| HTTP NodePort | `30080` |
| HTTPS NodePort | `30443` |
| Worker node | `192.168.1.71` |
| Cloudflare Tunnel VM | `192.168.1.50` |
| Application namespace | `narrativ` |
| Kubernetes profile | `dev` |

The initial Cloudflare Tunnel origin will be `http://192.168.1.71:30080`.

## 4. Responsibilities

| Component | Responsibility | Kubernetes access |
| --- | --- | --- |
| Developer | Pushes application changes | None required for normal development |
| Jenkins | Builds/tests the application, pushes Docker image, updates GitOps image tag | None |
| Argo CD | Detects GitOps changes and reconciles Kubernetes resources | Cluster deployment access |
| Cluster operator | Creates and rotates sensitive Kubernetes Secret manually | Limited `kubectl` access to namespace `narrativ-dev` |

Jenkins must not use `kubectl`, Helm deployment commands, or a cluster-admin kubeconfig. It does delivery to Git; Argo CD does deployment from Git.

## 5. Configuration model: ConfigMap and Secret

The application loads environment variables from both resources using `envFrom`.

### ConfigMap: safe to store in Git

Create `narrativ-config` in GitOps for non-sensitive values, including:

```dotenv
SPRING_PROFILES_ACTIVE=dev
DEV_INFRA_HOST=192.168.1.50
POSTGRES_PORT=5433
POSTGRES_DATABASE=narrativ
REDIS_PORT=6379
RABBITMQ_PORT=5672
ZIPKIN_PORT=9411
```

It also holds operational settings such as server port, logging level, rate limits, health endpoint settings, and non-sensitive MinIO endpoint/bucket configuration.

### Secret: never commit to Git

Create `narrativ-secrets` manually for sensitive values:

```dotenv
POSTGRES_USER=
POSTGRES_PASSWORD=
RABBITMQ_USERNAME=
RABBITMQ_NARRATIV_PASSWORD=
MAIL_USERNAME=
MAIL_PASSWORD=
GOOGLE_CLIENT_ID=
SECRET_KEY=
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=
```

Use a Kubernetes Secret rather than a ConfigMap. A Secret is Base64-encoded, not encrypted, so do not treat Base64 as protection. It is still the correct resource type because it has distinct RBAC controls and can be encrypted at rest in the Kubernetes datastore.

Detailed secret lifecycle instructions are in [deployment-secrets.md](deployment-secrets.md).

## 6. Prerequisites

Complete these before creating the Argo CD application:

1. Verify the RKE2 API is accessible to the administrator running `kubectl`.
2. Verify the infra VM can reach `192.168.1.71:30080`:

   ```bash
   curl -I http://192.168.1.71:30080
   ```

   A `404` response before the NARRATIV Ingress exists is acceptable; it proves the controller is reachable.

3. Confirm the worker node can reach `192.168.1.50` on all dependency ports: PostgreSQL `5433`, Redis `6379`, RabbitMQ `5672`, and Zipkin `9411`.
4. Ensure the GHCR package `ghcr.io/rizkysiregar/narrativ-service` is available to the cluster. If it becomes private, create an `imagePullSecret` and reference it in the Deployment.
5. Store the GitHub PAT credential (`github-all-repo-pat`, used for GHCR and GitOps) in Jenkins.
6. Revoke any token that has been committed in documentation or source history, and store replacement credentials only in Jenkins or the private environment file.

### Repair the ingress NodePort Service

The existing `ingress-nginx-external` NodePort Service may have been created by an older ingress-nginx installation. Its original selector uses `app.kubernetes.io/name=ingress-nginx`, but the RKE2-managed controller Pods use `app.kubernetes.io/name=rke2-ingress-nginx`. A mismatched selector produces no endpoints and causes every connection to port `30080` to be refused.

Confirm that before applying a fix:

```bash
kubectl -n kube-system describe svc ingress-nginx-external
```

If `Endpoints:` is empty, update the selector to match the RKE2 controller:

```bash
kubectl -n kube-system patch service ingress-nginx-external \
  --type merge \
  -p '{"spec":{"selector":{"app.kubernetes.io/component":"controller","app.kubernetes.io/instance":"rke2-ingress-nginx","app.kubernetes.io/name":"rke2-ingress-nginx"}}}'
```

Verify that endpoints are now populated and that the NodePort responds. A `404` response is expected until the NARRATIV Ingress exists.

```bash
kubectl -n kube-system describe svc ingress-nginx-external
kubectl -n kube-system get endpointslice -l kubernetes.io/service-name=ingress-nginx-external
curl -I http://192.168.1.71:30080
```

## 7. Prepare the private development environment file

On a protected administrator machine or protected infrastructure VM, create a file outside all Git repositories:

```text
/secure/narrativ/narrativ-dev.env
```

Set ownership and permissions so only the operator can read it:

```bash
sudo install -d -o "$USER" -g "$(id -g)" -m 700 /secure/narrativ
install -m 600 /dev/null /secure/narrativ/narrativ-dev.env
```

Populate the sensitive keys listed in the previous section from the existing local development `.env`. Do not copy this file to either application or GitOps repository.

Create or update the Kubernetes Secret before deploying the application:

```bash
kubectl create namespace narrativ-dev --dry-run=client -o yaml | kubectl apply -f -

grep -E '^(POSTGRES_USER|POSTGRES_PASSWORD|RABBITMQ_USERNAME|RABBITMQ_NARRATIV_PASSWORD|MAIL_USERNAME|MAIL_PASSWORD|GOOGLE_CLIENT_ID|SECRET_KEY|MINIO_ACCESS_KEY|MINIO_SECRET_KEY)=' \
  /secure/narrativ/narrativ-dev.env > /tmp/narrativ-secrets.env

kubectl -n narrativ-dev create secret generic narrativ-secrets \
  --from-env-file=/tmp/narrativ-secrets.env \
  --dry-run=client -o yaml | kubectl apply -f -

rm -f /tmp/narrativ-secrets.env
kubectl -n narrativ-dev describe secret narrativ-secrets
```

The final command must show only secret key names and never print their values.

## 8. Build the ConfigMap from the environment contract

Use `.env.example` as the complete configuration contract. Every variable used by `application.yml` and `application-dev.yml` must be supplied either by `narrativ-config` or `narrativ-secrets`; a missing required Spring placeholder prevents the application from starting.

For the `dev` profile:

- Copy non-sensitive variables from `.env.example` to `narrativ-config`.
- Set `SPRING_PROFILES_ACTIVE=dev`, not `local-dev`.
- Set `DEV_INFRA_HOST=192.168.1.50`.
- Move the sensitive keys listed in section 5 to `narrativ-secrets` only.

Before the first deployment, compare the ConfigMap and Secret key names against `.env.example`. Never copy the private `.env` file itself into a Pod or image.

After a successful rollout, verify the names without printing secret values:

```bash
kubectl -n narrativ-dev exec deployment/narrativ-app -- printenv | cut -d= -f1 | sort
```

## 9. Set JPA schema mode before deployment

The development database is shared with local development. Do not let a Kubernetes Pod change its schema automatically at startup.

Before the first deployment, change this in `src/main/resources/application-dev.yml`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

`validate` verifies that the database schema matches the application and fails safely if it does not. The current `update` mode can silently alter schema and is not appropriate once the database is shared by local and cluster workloads.

Because Flyway migrations were removed, future schema changes must be planned explicitly. Reintroducing versioned migrations before the next schema change is strongly recommended.

## 10. Create GitOps manifests

Create this directory in the `homelabs-gitops` repository:

```text
apps/narrativ-dev/
  namespace.yaml
  configmap.yaml
  deployment.yaml
  service.yaml
  ingress.yaml
  kustomization.yaml
```

Do not add a plaintext `secret.yaml`.

### Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: narrativ
```

### Deployment requirements

The Deployment must:

- Have one replica.
- Use image `ghcr.io/rizkysiregar/narrativ-service:<immutable-git-sha>`.
- Use `containerPort: 9091`.
- Load `narrativ-config` and `narrativ-secrets` with `envFrom`.
- Define startup, readiness, and liveness probes at `/api/v1/actuator/health/...` on port `9091`.
- Request/limit CPU and memory conservatively for the one-worker cluster.

### Service requirements

The Service must be `ClusterIP`, not NodePort:

```yaml
ports:
  - port: 80
    targetPort: 9091
```

Only `ingress-nginx` should be exposed through NodePort.

### Ingress requirements

The Ingress must use the selected hostname:

```yaml
spec:
  ingressClassName: nginx
  rules:
    - host: api-dev-narrativ.rizkysiregar.my.id
```

The backend is `narrativ-service` on port `80`.

### Kustomization image management

Kustomize should own the image tag, so Jenkins updates only one clear field:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - namespace.yaml
  - configmap.yaml
  - deployment.yaml
  - service.yaml
  - ingress.yaml
images:
  - name: ghcr.io/rizkysiregar/narrativ-service
    newTag: initial-placeholder
```

Run this locally in the GitOps repository before pushing:

```bash
kubectl kustomize apps/narrativ-dev
```

## 11. Create the Argo CD Application

Create this manually in the Argo CD dashboard, mirroring the portfolio application:

| Field | Value |
| --- | --- |
| Application name | `narrativ-dev` |
| Project | `default`, unless you already use a dedicated AppProject |
| Repository | `https://github.com/rizkysiregar/homelabs-gitops.git` |
| Revision | `main` |
| Path | `apps/narrativ-dev` |
| Cluster destination | `https://kubernetes.default.svc` |
| Namespace | `narrativ` |
| Sync | Automated |
| Automated options | Enable `Prune` and `Self Heal` |

Create the `narrativ-secrets` Secret first. Otherwise the Deployment cannot start because its required environment variables do not exist.

After Argo CD syncs, verify:

```bash
kubectl -n narrativ-dev get all
kubectl -n narrativ-dev get ingress
kubectl -n narrativ-dev rollout status deployment/narrativ-app
kubectl -n narrativ-dev logs deployment/narrativ-app --tail=100
```

## 12. Configure Cloudflare Tunnel

In the Cloudflare Zero Trust dashboard, add a public hostname route:

| Setting | Value |
| --- | --- |
| Public hostname | `api-dev-narrativ.rizkysiregar.my.id` |
| Service type | `HTTP` |
| Service URL | `http://192.168.1.71:30080` |

The request has this route:

```text
Cloudflare edge HTTPS → encrypted Cloudflare Tunnel → cloudflared on 192.168.1.50
→ private LAN HTTP to 192.168.1.71:30080 → ingress-nginx → NARRATIV
```

No Cloudflare route should point to Pod port `9091`.

The current portfolio Ingress is a catch-all rule without a hostname. Until the NARRATIV Ingress exists, requests through NodePort may reach portfolio. An exact NARRATIV hostname rule takes precedence once it is deployed.

After the route is created, test:

```bash
curl -i https://api-dev-narrativ.rizkysiregar.my.id/api/v1/actuator/health
```

## 13. Jenkins CI pipeline design

The NARRATIV `Jenkinsfile` updates `homelabs-gitops/apps/narrativ-dev/kustomization.yaml`; it does not update the legacy Helm chart that was removed from this repository.

Required stages:

1. Checkout `narrativ-backend` branch `master`.
2. Run `mvn clean test` using the Java version declared by `pom.xml`.
3. Run `mvn package` to produce `target/*.jar`.
4. Build image `ghcr.io/rizkysiregar/narrativ-service:<short-git-sha>`.
5. Push the immutable SHA image tag to GHCR. Updating `latest` is optional and must not be used by GitOps.
6. Clone `homelabs-gitops` branch `main` using the existing GitHub PAT credential.
7. Update only the matching `images[].newTag` value in `apps/narrativ-dev/kustomization.yaml` with `yq`.
8. Commit with a message such as `deploy(narrativ-dev): <git-sha>` and push to `main`.
9. Finish. Do not run `kubectl`, Helm upgrade, or Argo CD sync commands from Jenkins.

The pipeline uses the Java 25 Maven image declared in its configuration and makes tests a required gate. If tests fail, Jenkins stops before an image is published or a GitOps change is made. See `docs/jenkins-pipeline-setup.md` for the Jenkins credential and job setup.

## 14. Normal release procedure

1. Developer pushes a commit to `master`.
2. Jenkins produces and pushes an immutable image tagged with the commit SHA.
3. Jenkins updates the image tag in GitOps and pushes its commit.
4. Argo CD detects the GitOps commit and updates the single NARRATIV replica.
5. Confirm Argo CD reports `Synced` and `Healthy`.
6. Run the public health check from section 12.

## 15. Updating a secret manually

1. Update `/secure/narrativ/narrativ-dev.env`.
2. Repeat the Secret creation command in section 7.
3. Restart Pods because environment variables are read only at Pod startup:

   ```bash
   kubectl -n narrativ-dev rollout restart deployment/narrativ-app
   kubectl -n narrativ-dev rollout status deployment/narrativ-app
   ```

4. Verify logs and the health endpoint.

## 16. Rollback

For an application rollback, revert the image tag commit in `homelabs-gitops`, push it, and let Argo CD reconcile:

```bash
git revert <gitops-image-tag-commit>
git push origin main
```

For a configuration rollback, revert the relevant ConfigMap, Deployment, Service, or Ingress commit in the GitOps repository. Do not use `kubectl edit` for Git-managed resources because Argo CD will overwrite the manual change.

For a secret rollback, restore the previous protected `.env` version manually, reapply `narrativ-secrets`, then restart the Deployment.

## 17. Go/no-go checklist

Do not create the Argo CD Application until every required item is checked:

- [ ] The ingress NodePort Service has populated endpoints and is reachable from `192.168.1.50` at `192.168.1.71:30080`.
- [ ] `narrativ-secrets` exists in namespace `narrativ-dev`; its key names match section 5.
- [ ] `narrativ-config` provides every required non-sensitive variable from `.env.example` for profile `dev`.
- [ ] `application-dev.yml` uses `spring.jpa.hibernate.ddl-auto: validate`.
- [ ] The worker node can reach every external development dependency on `192.168.1.50`.
- [ ] A specific immutable image tag has been pushed to GHCR and is pullable by the cluster.
- [ ] The GitOps manifests render successfully with `kubectl kustomize apps/narrativ-dev`.
- [ ] The Ingress hostname is exactly `api-dev-narrativ.rizkysiregar.my.id`.
- [ ] The Cloudflare Tunnel public hostname routes to `http://192.168.1.71:30080`.
- [ ] Jenkins has no Kubernetes credentials and updates only the GitOps image tag.

### Logging note

`logback-spring.xml` already writes structured JSON logs to standard output. Kubernetes captures these logs, so use `kubectl logs` and do not add a hostPath volume for application logs. The current `LOGGING_FILE_PATH` value is not used by the configured console-only Logback appender.

## 18. Practical walkthrough: first deployment

This section is the exact order to use for NARRATIV's first deployment.

### Step 1 — make the application safe to start in the cluster

**Do this**

In `src/main/resources/application-dev.yml`, use:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Commit and push this application change before building a deployment image.

**Why**

The same development database is used by local development and the cluster. `update` allows whichever application starts first to modify tables automatically. `validate` only checks compatibility and refuses to start if the schema is wrong, which prevents accidental database changes during deployment.

**Checkpoint**

```bash
git diff HEAD -- src/main/resources/application-dev.yml
```

The file must show `ddl-auto: validate` before continuing.

### Step 2 — create the private credential source and Kubernetes Secret

**Do this**

Create `/secure/narrativ/narrativ-dev.env` outside any Git repository. Put only sensitive values in it:

```dotenv
POSTGRES_USER=
POSTGRES_PASSWORD=
RABBITMQ_USERNAME=
RABBITMQ_NARRATIV_PASSWORD=
MAIL_USERNAME=
MAIL_PASSWORD=
GOOGLE_CLIENT_ID=
SECRET_KEY=
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=
```

Then create the Secret:

```bash
kubectl create namespace narrativ-dev --dry-run=client -o yaml | kubectl apply -f -

kubectl -n narrativ-dev create secret generic narrativ-secrets \
  --from-env-file=/secure/narrativ/narrativ-dev.env \
  --dry-run=client -o yaml | kubectl apply -f -
```

**Why**

A Pod cannot read a file on the infra VM. Kubernetes must store the values as a Secret and inject them into the Pod when it starts. `--dry-run=client -o yaml | kubectl apply -f -` makes the command idempotent: it creates the Secret on the first run and updates it on later runs.

This Secret stays outside Git and outside Jenkins. Argo CD will reference it but will never manage or overwrite its content.

**Checkpoint**

```bash
kubectl -n narrativ-dev describe secret narrativ-secrets
```

Confirm that the expected key names appear. Do not run commands that print the secret values.

### Step 3 — create the GitOps application directory

**Do this**

Clone the GitOps repository and create the application directory:

```bash
git clone https://github.com/rizkysiregar/homelabs-gitops.git
cd homelabs-gitops
mkdir -p apps/narrativ-dev
```

Create these six files:

```text
apps/narrativ-dev/
  namespace.yaml
  configmap.yaml
  deployment.yaml
  service.yaml
  ingress.yaml
  kustomization.yaml
```

**Why**

This directory is the single desired-state definition of the non-sensitive NARRATIV deployment. Argo CD watches this directory rather than Jenkins or an operator manually running `kubectl apply`.

The six resources have distinct jobs:

| File | What it creates | Why it exists |
| --- | --- | --- |
| `namespace.yaml` | Namespace `narrativ` | Separates NARRATIV resources from other applications |
| `configmap.yaml` | `narrativ-config` | Supplies versioned non-sensitive environment settings |
| `deployment.yaml` | NARRATIV Pod controller | Keeps one application Pod running and rolls out image changes |
| `service.yaml` | `narrativ-service` ClusterIP | Gives NGINX a stable internal destination despite changing Pod IPs |
| `ingress.yaml` | Host routing rule | Routes the NARRATIV domain to the Service |
| `kustomization.yaml` | Kustomize application definition | Lets Jenkins update only the immutable image tag |

Do not create `secret.yaml`; Secret values must not enter Git.

**Checkpoint**

```bash
find apps/narrativ-dev -maxdepth 1 -type f | sort
```

It must list exactly the six files above.

### Step 4 — fill ConfigMap and workload manifests

**Do this**

Use `.env.example` from the NARRATIV application repository as the source-of-truth list. Put every non-sensitive key needed by the `dev` profile into `narrativ-config`.

Important values include:

```dotenv
SPRING_PROFILES_ACTIVE=dev
DEV_INFRA_HOST=192.168.1.50
SERVER_PORT=9091
SERVER_SERVLET_CONTEXT_PATH=/api/v1
POSTGRES_PORT=5433
POSTGRES_DATABASE=narrativ
REDIS_PORT=6379
RABBITMQ_PORT=5672
ZIPKIN_PORT=9411
```

The Deployment must load both configuration sources:

```yaml
envFrom:
  - configMapRef:
      name: narrativ-config
  - secretRef:
      name: narrativ-secrets
```

The Service must remain internal:

```yaml
spec:
  type: ClusterIP
  selector:
    app: narrativ-app
  ports:
    - port: 80
      targetPort: 9091
```

The Ingress must use the exact hostname:

```yaml
spec:
  ingressClassName: nginx
  rules:
    - host: api-dev-narrativ.rizkysiregar.my.id
```

**Why**

`envFrom` combines the two resources at container startup. The ConfigMap makes safe configuration reviewable in Git; the Secret supplies protected credentials.

The application Service is `ClusterIP` because it should be reachable only by the ingress controller inside Kubernetes. The existing `ingress-nginx-external` Service is the only NodePort gateway. Creating a NodePort per application would needlessly expose more network ports.

**Checkpoint**

Confirm all of these statements are true before committing:

- The Deployment label and Service selector both use `app: narrativ-app`.
- The Service has `targetPort: 9091`.
- The Ingress backend references `narrativ-service` port `80`.
- The Ingress has `ingressClassName: nginx`.
- No credential value appears in any GitOps file.

### Step 5 — render and push GitOps desired state

**Do this**

Render the resources locally:

```bash
kubectl kustomize apps/narrativ-dev > /tmp/narrativ-rendered.yaml
```

Review the output for the namespace, image, Service selector, and Ingress host. Then commit only the six non-secret files:

```bash
git add apps/narrativ-dev
git commit -m "feat: add narrativ dev GitOps manifests"
git push origin main
```

**Why**

Kustomize catches malformed YAML and missing resource references before Argo CD sees them. The Git commit becomes the audit trail and rollback point for deployment configuration.

**Checkpoint**

```bash
git status --short
git log -1 --oneline
```

The worktree should be clean and the GitOps commit should be visible on `main`.

### Step 6 — create the Argo CD Application

**Do this**

In the Argo CD dashboard, create an Application with:

```text
Name:        narrativ-dev
Repository:  https://github.com/rizkysiregar/homelabs-gitops.git
Revision:    main
Path:        apps/narrativ-dev
Destination: https://kubernetes.default.svc
Namespace:   narrativ
Sync policy: Automated, Prune enabled, Self Heal enabled
```

**Why**

An Argo CD Application is the bridge between a Git path and a Kubernetes namespace. Automated sync makes the Git commit the deployment trigger. Self-heal restores manually changed Git-managed resources. Prune removes resources that are intentionally deleted from the GitOps directory.

**Checkpoint**

In Argo CD, the application must show:

```text
Sync status: Healthy
Sync status: Synced
```

If it is not healthy, inspect Kubernetes before proceeding:

```bash
kubectl -n narrativ-dev get pods,svc,ingress
kubectl -n narrativ-dev describe pod -l app=narrativ-app
kubectl -n narrativ-dev logs deployment/narrativ-app --tail=100
```

### Step 7 — publish the hostname through Cloudflare Tunnel

**Do this**

In Cloudflare Zero Trust, create a public hostname:

```text
Public hostname: api-dev-narrativ.rizkysiregar.my.id
Service type:    HTTP
Service URL:     http://192.168.1.71:30080
```

Then test the health endpoint:

```bash
curl -i https://api-dev-narrativ.rizkysiregar.my.id/api/v1/actuator/health
```

**Why**

Cloudflare Tunnel makes a private NodePort reachable without exposing your LAN to the internet. NGINX then decides which application receives the request from its hostname. The route must target `30080`, not the NARRATIV container port `9091`.

**Checkpoint**

The public health endpoint must return HTTP `200`. A response from portfolio instead means the NARRATIV Ingress hostname does not exist or does not match exactly.

### Step 8 — automate future releases through Jenkins

**Do this**

Configure the NARRATIV Jenkins job to run the included Jenkinsfile. It will:

1. Build and test Java code.
2. Build and push `ghcr.io/rizkysiregar/narrativ-service:<commit-sha>`.
3. Clone `homelabs-gitops` branch `main`.
4. Change only `apps/narrativ-dev/kustomization.yaml` image `newTag`.
5. Commit and push the GitOps change.

**Why**

The commit SHA image tag is immutable: it always identifies exactly the code version being deployed. Jenkins cannot accidentally deploy to Kubernetes because it has no cluster credential; Argo CD deploys only the Git state that Jenkins updates.

**Checkpoint**

A Jenkins run should leave two auditable records:

- an image tag in GHCR (`ghcr.io/rizkysiregar/narrativ-service`);
- a `deploy(narrativ-dev): <commit-sha>` commit in `homelabs-gitops`.

Argo CD should then reconcile that GitOps commit automatically.

## 19. Post-deployment checks

```bash
kubectl -n narrativ-dev get pods,svc,ingress
kubectl -n narrativ-dev describe ingress narrativ-ingress
kubectl -n narrativ-dev rollout status deployment/narrativ-app
kubectl -n narrativ-dev logs deployment/narrativ-app --tail=100
curl -i https://api-dev-narrativ.rizkysiregar.my.id/api/v1/actuator/health
```

Successful deployment means the Pod is Ready, Argo CD is `Synced` and `Healthy`, the public health endpoint responds successfully, and no secret values appear in logs, manifests, Git, or Jenkins output.
