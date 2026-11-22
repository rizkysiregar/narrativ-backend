# NARRATIV dev secret deployment

This guide explains how the development environment variables are supplied to NARRATIV running in Kubernetes.

## Design

- Non-sensitive configuration is versioned in `homelabs-gitops/apps/narrativ-dev/configmap.yaml` and reconciled by Argo CD.
- Sensitive values are kept out of Git in a private `.env` file controlled by the cluster operator.
- The cluster operator creates or updates the Kubernetes Secret named `narrativ-secrets` in the `narrativ` namespace.
- The NARRATIV Deployment loads both resources with `envFrom`.
- Jenkins has no Kubernetes credentials. It only builds an image, pushes it to the registry, and updates the image tag in the GitOps repository. Argo CD performs the deployment.

Kubernetes cannot read a `.env` file located on a VM directly. The file must first be converted to a Kubernetes Secret.

## ConfigMap and Secret responsibilities

The deployment loads both resources as environment variables. The distinction is based on whether a value would be harmful if it were visible in Git.

| Resource | Managed by | Contains | Examples |
| --- | --- | --- | --- |
| `narrativ-config` ConfigMap | Argo CD from `homelabs-gitops` | Non-sensitive, environment-specific configuration | `DEV_INFRA_HOST`, database and service ports, `SPRING_PROFILES_ACTIVE=dev`, application limits |
| `narrativ-secrets` Secret | Cluster operator, manually | Credentials and cryptographic material | database password, RabbitMQ password, mail password, JWT `SECRET_KEY`, MinIO keys |

Do not put credentials in a ConfigMap. A Kubernetes Secret is encoded as Base64 in the API; Base64 is not encryption. Secrets are still the correct resource because they can have dedicated RBAC, can be encrypted at rest in the cluster datastore, and clearly communicate that their values must not be committed to Git.

## Deployment flow

```text
Developer pushes application code
        |
        v
Jenkins: test, build image, push to GHCR, update image tag in homelabs-gitops
        |
        v
Argo CD: detects the GitOps commit and applies ConfigMap, Deployment, Service, and Ingress
        |
        v
Pod: loads narrativ-config + narrativ-secrets via envFrom

Cluster operator: creates or rotates narrativ-secrets manually from private narrativ-dev.env
```

Jenkins never receives Kubernetes credentials and does not apply resources to the cluster.

## Private environment file

Create a private file named `narrativ-dev.env`. It may use the same values as local development, but must never be committed. Keep it only on a trusted administrator workstation or a protected infrastructure VM with access limited to the cluster operator.

The file must contain at least these sensitive keys:

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
# api secret of the Narrativ tenant in Marpokat (chat). Shown once by `marpokat tenant create` / `rotate-secret`
APP_MARPOKAT_API_SECRET=
```

The Marpokat base URL and api key are public and live in the ConfigMap (`APP_MARPOKAT_BASE_URL`,
`APP_MARPOKAT_API_KEY`). Without all three, `POST /chat/token` answers `503 CHAT_UNAVAILABLE` and the rest of the
app is unaffected.

Keep non-sensitive settings such as `DEV_INFRA_HOST`, ports, resource limits, and replica count in GitOps ConfigMaps instead. This makes normal configuration changes auditable in Git without exposing credentials.

## Initial bootstrap
   
Before Argo CD deploys the NARRATIV application, create the namespace and its secret from a trusted administrator workstation that has `kubectl` access to the RKE2 cluster.

```bash
kubectl create namespace narrativ-dev --dry-run=client -o yaml | kubectl apply -f -

grep -E '^(POSTGRES_USER|POSTGRES_PASSWORD|RABBITMQ_USERNAME|RABBITMQ_NARRATIV_PASSWORD|MAIL_USERNAME|MAIL_PASSWORD|GOOGLE_CLIENT_ID|SECRET_KEY|MINIO_ACCESS_KEY|MINIO_SECRET_KEY)=' \
  /secure/path/narrativ-dev.env > /tmp/narrativ-secrets.env

kubectl -n narrativ-dev create secret generic narrativ-secrets \
  --from-env-file=/tmp/narrativ-secrets.env \
  --dry-run=client -o yaml | kubectl apply -f -

rm -f /tmp/narrativ-secrets.env
```

Confirm that only key names are present; do not print secret values:

```bash
kubectl -n narrativ-dev describe secret narrativ-secrets
```

## Firebase service account key

The key is a multi-line JSON file, so it does not belong in `narrativ-secrets` (which is built from an env file). It lives in its own Secret that is mounted as a file. Each environment has its own Firebase project and its own Secret: `narrativ-firebase-dev` for dev, `narrativ-firebase-prod` for prod. The backend reads the project id from the key, so a pod can only push into the project of the key it was given.

```bash
kubectl -n narrativ-dev create secret generic narrativ-firebase-dev \
  --from-file=credentials.json=/secure/path/narrativ-firebase-adminsdk-<id>.json \
  --dry-run=client -o yaml | kubectl apply -f -
```

The Secret key must stay `credentials.json`: the Deployment mounts the Secret at `/etc/narrativ/firebase` and the ConfigMap sets `FIREBASE_CREDENTIALS_FILE=/etc/narrativ/firebase/credentials.json`. Check it without printing the private key:

```bash
kubectl -n narrativ-dev get secret narrativ-firebase-dev -o jsonpath='{.data.credentials\.json}' \
  | base64 -d | jq '{project_id, client_email, private_key_id}'
```

Rotating the key: create a new key in the Firebase console, run the command above with the new file, restart the deployment (the key is read at startup), then delete the old key in the console. The application refuses to start when the key is missing or unreadable.

For local development set `FIREBASE_CREDENTIALS_FILE` in `.env` to the path of the dev key. Never commit a key file.

## Updating credentials

Whenever a credential changes:

1. Update the private `narrativ-dev.env` file in its protected location.
2. Run the secret update commands from the bootstrap step with the updated private environment file.
3. Restart the deployment so environment variables are reloaded:

   ```bash
   kubectl -n narrativ-dev rollout restart deployment/narrativ-app
   kubectl -n narrativ-dev rollout status deployment/narrativ-app
   ```

Changing a Kubernetes Secret does not update environment variables in existing Pods automatically.

## Later improvement

The manual Kubernetes Secret is a good starting point for a homelab. For a more mature setup, use one of these approaches:

- **External Secrets**: a controller reads values from a secret manager such as Vault, 1Password, AWS Secrets Manager, or Infisical and creates `narrativ-secrets` inside the cluster.
- **Sealed Secrets**: encrypted secret manifests can be committed to Git and are decrypted only by the controller in the cluster.

The application manifest can remain unchanged in either approach because it will still refer to the same `narrativ-secrets` Kubernetes Secret.
