# Kubernetes (local)

Compose is enough for day-to-day development. This overlay is the portfolio piece: service discovery, probes, Secrets, persistent volumes, Ingress, and HPA.

The local overlay (`k8s/overlays/local`) targets Docker Desktop Kubernetes or kind. Images are not pushed to a registry; they are loaded as `courier-tracking-api:local` and `courier-tracking-frontend:local`.

```text
Ingress / NodePort
        │
        ▼
   frontend (nginx)  ── /api  /ws-courier  /swagger-ui ──►  api (Spring Boot)
                                                              │
                                    ┌─────────────────────────┼─────────────────────────┐
                                    ▼                         ▼                         ▼
                               postgres (STS+PVC)           redis                  rabbitmq (STOMP)
```

The production frontend image proxies same-origin; the browser does not call `localhost:8080` directly. The API waits on `GET /actuator/health/liveness` and `GET /actuator/health/readiness`.

## Deploy

From the **repository root**:

```powershell
.\scripts\k8s-deploy.ps1
```

Linux / macOS:

```bash
chmod +x scripts/k8s-deploy.sh
./scripts/k8s-deploy.sh
```

kind (optional):

```powershell
.\scripts\k8s-deploy.ps1 -Kind
```

```bash
./scripts/k8s-deploy.sh --kind
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:30080 |
| Swagger | http://localhost:30808/swagger-ui.html |
| Health | http://localhost:30808/actuator/health |

If NodePort does not open in the browser:

```bash
kubectl -n courier-tracking port-forward svc/courier-frontend 18080:80
```

Then open http://127.0.0.1:18080

## Apply by hand

```bash
docker build -t courier-tracking-api:local .
docker build -t courier-tracking-frontend:local -f frontend/Dockerfile.prod frontend
kubectl apply -k k8s/overlays/local
kubectl -n courier-tracking get pods,svc
```

## What is in the overlay

| Piece | Role |
|-------|------|
| Namespace `courier-tracking` | Isolates from other local workloads |
| ConfigMap / Secret | Non-secret config vs passwords / JWT |
| Postgres StatefulSet + PVC | Stable identity and disk for the database |
| Redis / RabbitMQ Deployment | GEO index and STOMP relay |
| Init container | API does not start until 5432 / 6379 / 61613 are ready |
| startup / liveness / readiness | Spring Actuator probes |
| Ingress + NodePort | In-cluster HTTP and local browser access |
| HPA (CPU 70%, 1–3 replicas) | Object exists; it does not scale without metrics-server |

`k8s/base/secret.yaml` is **local only**. On a real cluster:

```bash
kubectl -n courier-tracking create secret generic courier-secrets --from-env-file=.env --dry-run=client -o yaml | kubectl apply -f -
```

`JWT_SECRET` must be Base64 and at least 32 bytes (`openssl rand -base64 32`).

## Docker Desktop kind mode

Docker Desktop can install Kubernetes in two ways (Settings → Kubernetes → Cluster settings):

| Mode | Node name | Local image behavior |
|------|-----------|----------------------|
| **kind** (current default) | `desktop-control-plane` | Images from `docker build` are **not** visible to the cluster |
| **Kubeadm** | `docker-desktop` | Local images are used directly |

In kind mode you get `ErrImageNeverPull` / `ErrImagePull`. The deploy script detects the node and loads the image. To do it manually:

```powershell
# Node container must be visible:
# Settings > Kubernetes > Show system containers (advanced) > Apply
docker save courier-tracking-api:local -o api.tar
docker cp api.tar desktop-control-plane:/api.tar
docker exec desktop-control-plane ctr -n k8s.io images import /api.tar
```

Do **not** pipe `docker save ... | docker exec -i ... ctr images import -` in PowerShell; the binary stream is corrupted (`archive/tar: invalid tar header`).

In the same mode, NodePort is not published on the host (`localhost:30080` does not connect). Use port-forward as above.

A local HTTP registry (`localhost:5000`) also fails in this mode: pulls go through Docker Desktop’s `registry-mirror` and return `500 Internal Server Error`.

## First user

Cluster Postgres starts empty. Flyway creates the schema only — no users. Register before logging into the panel.

PowerShell:

```powershell
$b = @{ fullName="Berk Mermer"; email="berk@example.com"; phoneNumber="+905551112233"; password="securePass123" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:18080/api/v1/auth/register -ContentType "application/json" -Body $b
```

Do not use `curl.exe` with `-d "{\"...\"}"` on PowerShell 5.1; quoting breaks and you get `JSON parse error`.

## Docker Compose on Windows

If host port **61613** is already taken (Hyper-V), set `RABBITMQ_STOMP_PORT=62613` in `.env`. The container still listens on 61613 internally.

## Teardown

```bash
kubectl delete -k k8s/overlays/local
```

If you used kind:

```bash
kind delete cluster --name courier
```
