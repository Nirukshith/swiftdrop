# SwiftDrop — Kubernetes Deployment (Minikube)

Minikube-compatible Kubernetes manifests for all SwiftDrop services.

## Prerequisites

- [Minikube](https://minikube.sigs.k8s.io/docs/start/) ≥ 1.33
- [kubectl](https://kubernetes.io/docs/tasks/tools/) ≥ 1.29
- Docker (for local image builds)

---

## Quick Start

### 1. Start Minikube

```bash
minikube start --memory=6144 --cpus=4
```

### 2. Point Docker to Minikube's daemon

```bash
eval $(minikube docker-env)
```

### 3. Build all service images inside Minikube

```bash
# Run from the repository root
docker build -t swiftdrop/auth-service:latest        -f deploy/dockerfiles/auth-service.Dockerfile .
docker build -t swiftdrop/parcel-service:latest      -f deploy/dockerfiles/parcel-service.Dockerfile .
docker build -t swiftdrop/operations-service:latest  -f deploy/dockerfiles/operations-service.Dockerfile .
docker build -t swiftdrop/integration-service:latest -f deploy/dockerfiles/integration-service.Dockerfile .
```

### 4. Populate secrets

> **Never commit real secrets.** Edit `secrets.yaml` or use the imperative command:

```bash
kubectl create secret generic swiftdrop-secrets \
  --namespace=swiftdrop \
  --from-literal=POSTGRES_PASSWORD='your-db-password' \
  --from-literal=INTERNAL_NOTIFICATIONS_SERVICE_TOKEN='your-notif-token' \
  --from-literal=INTERNAL_MERCHANT_SERVICE_TOKEN='your-merchant-token' \
  --from-literal=GOOGLE_CLIENT_ID='your-google-client-id' \
  --from-literal=GOOGLE_CLIENT_SECRET='your-google-client-secret' \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 5. Apply all manifests

```bash
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/secrets.yaml        # skip if you used step 4
kubectl apply -f deploy/k8s/postgres-statefulset.yaml
kubectl apply -f deploy/k8s/kafka-statefulset.yaml
kubectl apply -f deploy/k8s/mailpit-deployment.yaml
kubectl apply -f deploy/k8s/auth-service-deployment.yaml
kubectl apply -f deploy/k8s/parcel-service-deployment.yaml
kubectl apply -f deploy/k8s/operations-service-deployment.yaml
kubectl apply -f deploy/k8s/integration-service-deployment.yaml
kubectl apply -f deploy/k8s/nginx-deployment.yaml
```

Or all at once:

```bash
kubectl apply -f deploy/k8s/
```

### 6. Wait for pods to become ready

```bash
kubectl rollout status deployment -n swiftdrop --timeout=300s
```

### 7. Access the gateway

```bash
minikube service nginx-gateway -n swiftdrop --url
# → http://<minikube-ip>:30080
```

---

## Manifest Overview

| File | Contents |
|---|---|
| `namespace.yaml` | `swiftdrop` Namespace |
| `configmap.yaml` | Non-secret environment variables shared across services |
| `secrets.yaml` | Placeholder Secret — **replace before applying** |
| `postgres-statefulset.yaml` | PostgreSQL 16 StatefulSet + PVC + headless Service |
| `kafka-statefulset.yaml` | Apache Kafka 3.8.1 StatefulSet + PVC + topic-init Job |
| `mailpit-deployment.yaml` | Mailpit SMTP/UI Deployment + Service |
| `auth-service-deployment.yaml` | auth-service Deployment + ClusterIP Service |
| `parcel-service-deployment.yaml` | parcel-service Deployment + ClusterIP Service |
| `operations-service-deployment.yaml` | operations-service Deployment + ClusterIP Service |
| `integration-service-deployment.yaml` | integration-service Deployment + ClusterIP Service |
| `nginx-deployment.yaml` | Nginx gateway Deployment + NodePort Service (`:30080`) |

---

## Dry-run Validation

```bash
kubectl apply --dry-run=client -f deploy/k8s/
```

---

## Teardown

```bash
kubectl delete namespace swiftdrop
minikube stop
```

