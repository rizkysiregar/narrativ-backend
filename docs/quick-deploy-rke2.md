# Feedzmore RKE2 quick deployment

Use this after the RKE2 ingress NodePort is healthy at `192.168.1.71:30080`.

## 1. Build the current application image

Run in `feedzmore-backend`:

```bash
docker login
make image-push TAG=latest
```

Why: `make image-push` packages the application and uses Docker Buildx to build a `linux/amd64` image. This is required because the MacBook M4 is ARM64 while the RKE2 nodes are AMD64 Linux. The first GitOps deployment uses the `latest` image configured in `apps/feedzmore/kustomization.yaml`; Jenkins will later replace this with immutable commit-SHA tags.

## 2. Create the private Kubernetes Secret

The prepared private file is `deploy/feedzmore-dev.secrets.env`. It is ignored by Git. Copy it to the secure location on the infra VM or your administrator workstation:

```bash
sudo install -d -o "$USER" -g "$(id -g)" -m 700 /secure/feedzmore
install -m 600 deploy/feedzmore-dev.secrets.env /secure/feedzmore/feedzmore-dev.env
```

From a machine with `kubectl` access to RKE2, run:

```bash
kubectl create namespace feedzmore --dry-run=client -o yaml | kubectl apply -f -

kubectl -n feedzmore create secret generic feedzmore-secrets \
  --from-env-file=/secure/feedzmore/feedzmore-dev.env \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n feedzmore describe secret feedzmore-secrets
```

Why: the Deployment reads passwords and keys from this Secret. Do not commit this file or create a plaintext `secret.yaml`.

## 3. Review and push GitOps manifests

The manifests are already prepared in:

```text
homelabs-gitops/apps/feedzmore/
```

Render them first:

```bash
cd /Users/rizkysiregar/Documents/work/personal-project/homelabs-gitops
kubectl kustomize apps/feedzmore
git add apps/feedzmore
git commit -m "feat: add feedzmore dev deployment"
git push origin main
```

Why: Argo CD deploys only the committed desired state in this directory. The manifests contain ConfigMap, Deployment, Service, and Ingress, but no credential values.

## 4. Create the Argo CD Application

In the Argo CD UI, create:

```text
Application name: feedzmore-dev
Repository:       https://github.com/rizkysiregar/homelabs-gitops.git
Revision:         main
Path:             apps/feedzmore
Destination:      https://kubernetes.default.svc
Namespace:        feedzmore
Sync policy:      Automated, Prune, Self Heal
```

Wait until the application is `Synced` and `Healthy`.

## 5. Configure Cloudflare Tunnel

Create this public hostname in Cloudflare Zero Trust:

```text
Hostname:    api-dev-feedzmore.rizkysiregar.my.id
Service:     HTTP
URL:         http://192.168.1.71:30080
```

Why: NodePort `30080` reaches the shared NGINX Ingress Controller, which routes this hostname to Feedzmore. Do not use port `9091` here.

## 6. Verify

```bash
kubectl -n feedzmore get pods,svc,ingress
kubectl -n feedzmore rollout status deployment/feedzmore-app
kubectl -n feedzmore logs deployment/feedzmore-app --tail=100
curl -i https://api-dev-feedzmore.rizkysiregar.my.id/api/v1/actuator/health
```

The public health endpoint must return HTTP `200`.
