<div align="center">

# Live Courier Tracking

**Real-time courier location tracking, Redis GEO assignment, and live map updates over STOMP.**

Internship / portfolio demo — intentionally scoped, not a production product.

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-STOMP-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Kustomize-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)](https://kubernetes.org/)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)

[Live Demo Flow](#usage) · [API Reference](#api-reference) · [Report Bug](https://github.com/BerkMermer/live-courier-tracking/issues)

</div>

---

## Table of Contents

- [About The Project](#about-the-project)
- [Architecture](#architecture)
- [Built With](#built-with)
- [Features](#features)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [API Reference](#api-reference)
- [Frontend](#frontend)
- [Kubernetes](#kubernetes)
- [Project Structure](#project-structure)
- [Security](#security)
- [Testing](#testing)
- [License](#license)
- [Contact](#contact)

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## About The Project

Courier Tracking is a **REST + realtime** backend with a small React panel. A customer places an order, the nearest available courier is assigned, and the courier’s location is streamed to the map over WebSocket.

| | |
|---|---|
| **Auth** | JWT with roles `CUSTOMER`, `COURIER`, `ADMIN` |
| **Assignment** | Redis GEO, with Haversine fallback when Redis is empty |
| **Realtime** | RabbitMQ STOMP broker relay |
| **Orders** | UUID `trackingNumber`, soft delete, ownership checks (BOLA mitigation) |
| **Ops** | Docker Compose one-command stack, Kubernetes (Kustomize) with probes, Secret/ConfigMap, StatefulSet, Ingress, HPA |

### Screenshots

![Login](docs/screenshots/login.png)

![Live tracking](docs/screenshots/live-tracking.png)

![Order panel](docs/screenshots/order-panel.png)

![Swagger UI](docs/screenshots/swagger.png)

### Out of scope

| Topic | Note |
|-------|------|
| Guest checkout | Authentication is required |
| Courier self-registration | Courier accounts come from seed / SQL |
| Payments, push notifications, admin UI | Product scope, not part of this demo |

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## Architecture

![System architecture](docs/architecture.png)

Location updates are published on:

```text
/topic/courier-location.{courierId}
```

RabbitMQ’s nested STOMP destinations do not accept `/` in the topic path, so a `.` separator is used.

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## Built With

| Layer | Stack |
|-------|--------|
| Backend | Java 17, Spring Boot **4.0.7**, Spring Security + JWT, Spring Data JPA |
| Database / cache | PostgreSQL 16, Flyway, Redis 7 (GEO) |
| Realtime | STOMP WebSocket + SockJS, RabbitMQ (broker relay) |
| API docs | SpringDoc OpenAPI (Swagger UI) |
| Frontend | React 18, Vite, Tailwind CSS, Leaflet + OSRM routing |
| Test / ops | JUnit, Mockito, Testcontainers, Docker Compose, Kubernetes (Kustomize) |

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## Features

### Authentication

- `POST /register` and `POST /login` return a JWT
- Public registration always creates a `CUSTOMER`
- Endpoints use `@PreAuthorize` plus ownership checks in the service layer

### Orders

- Create, list (`/me`), detail, and cancel (`PENDING` only)
- UUID `trackingNumber` on every order
- `assign-courier` picks the nearest `AVAILABLE` courier

### Courier location

- `PUT /couriers/location` writes to PostgreSQL, Redis GEO, and STOMP
- If Redis has no GEO data, assignment falls back to Haversine

### Live map

- Road route via OSRM, motorcycle marker, remaining distance and ETA
- Active order plus order history

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## Getting Started

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and Docker Compose
- Git

For local Maven tests without Compose: JDK 17.

### Installation (Docker Compose)

```bash
git clone https://github.com/BerkMermer/live-courier-tracking.git
cd live-courier-tracking

cp .env.example .env
```

Edit `.env` and set `POSTGRES_PASSWORD` and `JWT_SECRET`. Generate a secret with:

```bash
openssl rand -base64 32
```

On Windows, if host port **61613** is already taken, set `RABBITMQ_STOMP_PORT=62613` in `.env`.

```bash
docker compose up --build -d
```

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Frontend | http://localhost:3000 |
| RabbitMQ Management | http://localhost:15672 |

```bash
docker compose logs -f app
docker compose down
```

Do not commit `.env`. Only `.env.example` belongs in the repository.

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## Usage

### Swagger demo flow

1. `POST /api/v1/auth/register` — create a customer
2. `POST /api/v1/auth/login` — copy the JWT, then **Authorize** in Swagger (`Bearer <token>`)
3. `POST /api/v1/orders` — create an order with pickup lat/lng
4. With a courier JWT, `PUT /api/v1/couriers/location`
5. `POST /api/v1/orders/{id}/assign-courier`
6. Open http://localhost:3000 and watch the map

Courier accounts are seeded. There is no public courier-register endpoint.

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## API Reference

Interactive docs: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Auth — `/api/v1/auth`

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| POST | `/register` | Public | Register and return JWT |
| POST | `/login` | Public | Login and return JWT |

### Orders — `/api/v1/orders`

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| POST | `/` | CUSTOMER | Create order |
| GET | `/me` | CUSTOMER | List own orders |
| GET | `/{orderId}` | CUSTOMER / COURIER / ADMIN | Order detail (ownership enforced) |
| POST | `/{orderId}/cancel` | CUSTOMER | Cancel order |
| POST | `/{orderId}/assign-courier` | ADMIN / COURIER | Assign nearest courier |

### Couriers — `/api/v1/couriers`

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| PUT | `/location` | COURIER | Update location |
| GET | `/{id}/location` | CUSTOMER / ADMIN | Get courier location |
| GET | `/me/location` | COURIER | Get own location |

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## Frontend

React + Leaflet live tracking panel, served on `:3000` with Compose.

- CARTO Voyager basemap and OSRM road routing
- Motorcycle icon, pickup marker, distance / ETA
- Order history via `GET /orders/me`

Run the UI locally without Compose:

```bash
cd frontend
npm install
npm run dev
```

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## Kubernetes

Compose is enough for day-to-day development. The Kubernetes overlay is the portfolio piece: service discovery, probes, Secrets, persistent volumes, Ingress, and HPA.

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

### Windows (Docker Desktop)

1. Docker Desktop → Settings → Kubernetes → **Enable Kubernetes** → Apply.
2. PowerShell:

```powershell
cd "c:\Users\Berk Mermer\Desktop\Projects\courier-tracking-api"
.\scripts\k8s-deploy.ps1
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:30080 |
| Swagger | http://localhost:30808/swagger-ui.html |
| Health | http://localhost:30808/actuator/health |

If NodePort does not open in the browser:

```powershell
kubectl -n courier-tracking port-forward svc/courier-frontend 18080:80
```

Then open http://127.0.0.1:18080

### kind (optional)

```powershell
.\scripts\k8s-deploy.ps1 -Kind
```

Linux / macOS:

```bash
chmod +x scripts/k8s-deploy.sh
./scripts/k8s-deploy.sh          # add --kind for kind
```

### Apply by hand

```bash
docker build -t courier-tracking-api:local .
docker build -t courier-tracking-frontend:local -f frontend/Dockerfile.prod frontend
kubectl apply -k k8s/overlays/local
kubectl -n courier-tracking get pods,svc
```

### What is in the overlay, and why

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

### Docker Desktop kind mode

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

In the same mode, NodePort is not published on the host (`localhost:30080` does not connect). Use port-forward:

```powershell
kubectl -n courier-tracking port-forward svc/courier-frontend 18080:80
```

A local HTTP registry (`localhost:5000`) also fails in this mode: pulls go through Docker Desktop’s `registry-mirror` and return `500 Internal Server Error`.

### First user

Cluster Postgres starts empty. Flyway creates the schema only — no users. Register before logging into the panel:

```powershell
$b = @{ fullName="Berk Mermer"; email="berk@example.com"; phoneNumber="+905551112233"; password="securePass123" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:18080/api/v1/auth/register -ContentType "application/json" -Body $b
```

Do not use `curl.exe` with `-d "{\"...\"}"` on PowerShell 5.1; quoting breaks and you get `JSON parse error`.

### Teardown

```powershell
kubectl delete -k k8s/overlays/local
# if you used kind: kind delete cluster --name courier
```

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## Project Structure

```text
live-courier-tracking/
├── src/main/java/com/berk/courier_tracking_api/
│   ├── config/          # Redis, Swagger, WebSocket (RabbitMQ relay)
│   ├── controller/      # Auth, Order, Courier
│   ├── dto/ entity/ enums/ exception/ repository/
│   ├── security/        # JWT filter, WebSocket auth
│   ├── service/         # Order, Courier, Redis GEO
│   └── util/            # Haversine
├── src/main/resources/
│   ├── application.yaml
│   └── db/migration/    # Flyway
├── frontend/            # React + Vite + Leaflet (k8s: nginx production image)
├── k8s/
│   ├── base/            # Namespace, ConfigMap, Secret, STS, Deploy, Ingress, HPA
│   ├── overlays/local/  # NodePort + local image tag
│   └── kind-cluster.yaml
├── scripts/             # k8s-deploy.ps1 / k8s-deploy.sh
├── docs/
│   ├── architecture.png
│   └── screenshots/
├── Dockerfile
├── docker-compose.yml
└── README.md
```

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## Security

| Topic | Approach |
|-------|----------|
| Auth | JWT (HS256), stateless |
| BOLA | Ownership checks in the service layer |
| Roles | Registration is always `CUSTOMER` |
| Passwords | BCrypt |
| Soft delete | `deleted_at` + `@SQLRestriction` |
| CORS | Allowlist (`app.cors.allowed-origins`) |
| Actuator | `/actuator/health` is public; other actuator endpoints are not exposed |

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## Testing

```bash
./mvnw test      # Linux / macOS
mvnw.cmd test    # Windows
```

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## License

Educational / portfolio use. Contact the author before any commercial use.

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>

---

## Contact

**Berk Mermer** · [GitHub](https://github.com/BerkMermer)

Project: [https://github.com/BerkMermer/live-courier-tracking](https://github.com/BerkMermer/live-courier-tracking)

<p align="right">(<a href="#live-courier-tracking">back to top</a>)</p>
