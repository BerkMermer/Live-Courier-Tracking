# Courier Tracking API

> Gerçek zamanlı kurye konum takibi, Redis GEO ile akıllı atama ve STOMP ile canlı izleme.  
> **Staj / portfolio demosu** — production ürünü değil; bilinçli olarak dar tutulmuş bir öğrenme projesidir.

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)](https://redis.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-STOMP-FF6600?logo=rabbitmq)](https://www.rabbitmq.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://www.docker.com/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Kustomize-326CE5?logo=kubernetes)](https://kubernetes.io/)

---

## İçindekiler

- [Proje Hakkında](#proje-hakkında)
- [Sistem Mimarisi](#sistem-mimarisi)
- [Teknoloji Stack](#teknoloji-stack)
- [Özellikler](#özellikler)
- [API Endpoint’leri](#api-endpointleri)
- [Kurulum (Docker)](#kurulum-docker)
- [Kubernetes](#kubernetes)
- [Kullanım](#kullanım)
- [Frontend](#frontend)
- [Proje Yapısı](#proje-yapısı)
- [Güvenlik](#güvenlik)
- [Test](#test)
- [Geliştirici](#geliştirici)
- [Lisans](#lisans)

---

## Proje Hakkında

Courier Tracking API; müşterinin sipariş oluşturduğu, en yakın müsait kuryenin atandığı ve kurye konumunun WebSocket üzerinden canlı izlendiği bir **REST + realtime** backend demosudur. Küçük bir React paneli ile haritada takip gösterilir.

**Temel yetenekler**

- JWT + roller: `CUSTOMER` / `COURIER` / `ADMIN`
- Redis GEO + Haversine fallback ile yakın kurye atama
- RabbitMQ STOMP relay ile konum yayını
- Soft delete, UUID `trackingNumber`, sahiplik kontrolü (BOLA önlemi)
- Docker Compose ile tek komutta stack
- Kubernetes (Kustomize): probe, Secret/ConfigMap, StatefulSet, Ingress, HPA

### Bilinçli kapsam dışı

| Konu | Not |
|------|-----|
| Misafir sipariş | Auth zorunlu |
| Kurye self-register | Seed / SQL ile `COURIER` |
| AI chat | Kaldırıldı |
| Ödeme, push, admin paneli | Ürün kapsamı dışı |

---

## Sistem Mimarisi

![Sistem mimarisi](docs/architecture.png)

WebSocket konum topic’i (RabbitMQ nested `/` kabul etmez): `/topic/courier-location.{courierId}`

---

## Teknoloji Stack

| Katman | Teknoloji |
|--------|-----------|
| Backend | Java 17, Spring Boot **4.0.7**, Spring Security + JWT, Spring Data JPA |
| DB / cache | PostgreSQL 16, Flyway, Redis 7 (GEO) |
| Realtime | STOMP WebSocket + SockJS, RabbitMQ (broker relay) |
| API docs | SpringDoc OpenAPI (Swagger UI) |
| Frontend | React, Vite, Tailwind, Leaflet + OSRM rota |
| Test / ops | JUnit, Mockito, Testcontainers, Docker Compose, Kubernetes (Kustomize) |

---

## Özellikler

### Kimlik doğrulama

- `POST /register`, `POST /login` → JWT
- Kayıtta rol sabit: `CUSTOMER`
- Endpoint’lerde `@PreAuthorize` + sahiplik kontrolü

### Siparişler

- Oluşturma, liste (`/me`), detay, iptal (`PENDING`)
- UUID `trackingNumber`
- `assign-courier`: en yakın `AVAILABLE` kurye

### Kurye / konum

- `PUT /couriers/location` → Postgres + Redis GEO + STOMP
- Redis boşsa Haversine fallback

### Frontend izleme

- Yol rotası (OSRM), motor ikonu, mesafe / ETA
- Aktif sipariş + sipariş geçmişi

---

## API Endpoint’leri

### Auth — `/api/v1/auth`

| Method | Path | Yetki | Açıklama |
|--------|------|-------|----------|
| POST | `/register` | Public | Kayıt → JWT |
| POST | `/login` | Public | Giriş → JWT |

### Orders — `/api/v1/orders`

| Method | Path | Yetki | Açıklama |
|--------|------|-------|----------|
| POST | `/` | CUSTOMER | Sipariş oluştur |
| GET | `/me` | CUSTOMER | Kendi siparişleri |
| GET | `/{orderId}` | CUSTOMER / COURIER / ADMIN | Detay (sahiplik) |
| POST | `/{orderId}/cancel` | CUSTOMER | İptal |
| POST | `/{orderId}/assign-courier` | ADMIN / COURIER | En yakın kurye |

### Couriers — `/api/v1/couriers`

| Method | Path | Yetki | Açıklama |
|--------|------|-------|----------|
| PUT | `/location` | COURIER | Konum güncelle |
| GET | `/{id}/location` | CUSTOMER / ADMIN | Konum getir |
| GET | `/me/location` | COURIER | Kendi konumu |

> Swagger: http://localhost:8080/swagger-ui.html — Authorize: `Bearer <JWT>`

---

## Kurulum (Docker)

```bash
git clone https://github.com/BerkMermer/Live-Courier-Tracking.git
cd Live-Courier-Tracking

cp .env.example .env
# .env içinde POSTGRES_PASSWORD ve JWT_SECRET doldurun
# Windows'ta 61613 çakışırsa: RABBITMQ_STOMP_PORT=62613

docker compose up --build -d
```

| Servis | URL |
|--------|-----|
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| Frontend | http://localhost:3000 |
| RabbitMQ UI | http://localhost:15672 |

```bash
docker compose logs -f app
docker compose down
```

> `.env` dosyasını **asla** commit etmeyin (`.gitignore`’da). GitHub’a yalnızca `.env.example` gider.

---

## Kubernetes

Günlük geliştirme için Compose yeter. Staj / portföy tarafında asıl kazanç Kubernetes: servis keşfi, probe, Secret, kalıcı volume, Ingress ve HPA.

Yerel overlay (`k8s/overlays/local`) Docker Desktop Kubernetes veya kind içindir. Image’lar registry’ye push edilmez; `courier-tracking-api:local` ve `courier-tracking-frontend:local` olarak cluster’a yüklenir.

```
Ingress / NodePort
        │
        ▼
   frontend (nginx)  ── /api  /ws-courier  /swagger-ui ──►  api (Spring Boot)
                                                              │
                                    ┌─────────────────────────┼─────────────────────────┐
                                    ▼                         ▼                         ▼
                               postgres (STS+PVC)           redis                  rabbitmq (STOMP)
```

Frontend production image same-origin proxy kullanır; tarayıcı `localhost:8080`’e gitmez. API `GET /actuator/health/liveness|readiness` ile ayağa kalkmayı bekler.

### Windows (Docker Desktop) — önerilen

1. Docker Desktop → Settings → Kubernetes → **Enable Kubernetes** → Apply.
2. PowerShell:

```powershell
cd "c:\Users\Berk Mermer\Desktop\Projects\courier-tracking-api"
.\scripts\k8s-deploy.ps1
```

| Servis | URL |
|--------|-----|
| Frontend | http://localhost:30080 |
| Swagger | http://localhost:30808/swagger-ui.html |
| Health | http://localhost:30808/actuator/health |

NodePort tarayıcıda açılmazsa:

```powershell
kubectl -n courier-tracking port-forward svc/courier-frontend 18080:80
```

Sonra http://127.0.0.1:18080

### kind (opsiyonel)

```powershell
.\scripts\k8s-deploy.ps1 -Kind
```

Linux / macOS: `chmod +x scripts/k8s-deploy.sh && ./scripts/k8s-deploy.sh` (`--kind` ile kind).

### Elle uygulamak

```bash
docker build -t courier-tracking-api:local .
docker build -t courier-tracking-frontend:local -f frontend/Dockerfile.prod frontend
kubectl apply -k k8s/overlays/local
kubectl -n courier-tracking get pods,svc
```

### Ne var, neden var

| Parça | Neden |
|-------|--------|
| Namespace `courier-tracking` | Diğer local workload’lardan ayrışır |
| ConfigMap / Secret | Non-secret config vs şifre/JWT ayrımı |
| Postgres StatefulSet + PVC | DB için kimlik + disk |
| Redis / RabbitMQ Deployment | GEO index ve STOMP relay |
| Init container | API, 5432 / 6379 / 61613 hazır olmadan start etmez |
| startup / liveness / readiness | Spring Actuator probe |
| Ingress + NodePort | Cluster içi HTTP ve local tarayıcı |
| HPA (CPU %70, 1–3 replica) | metrics-server yoksa ölçeklenmez, obje durur |

`k8s/base/secret.yaml` **sadece local**. Gerçek cluster’da:

```bash
kubectl -n courier-tracking create secret generic courier-secrets --from-env-file=.env --dry-run=client -o yaml | kubectl apply -f -
```

`JWT_SECRET` Base64 ve en az 32 byte olmalı (`openssl rand -base64 32`).

### Docker Desktop kind modu (sık takılan yer)

Docker Desktop, Kubernetes'i iki şekilde kurabiliyor (Settings → Kubernetes → *Cluster settings*):

| Mod | Node adı | Yerel imaj davranışı |
|-----|----------|----------------------|
| **kind** (yeni varsayılan) | `desktop-control-plane` | `docker build` imajları cluster'a **görünmez** |
| **Kubeadm** | `docker-desktop` | Yerel imajlar doğrudan kullanılır |

kind modundaysan `ErrImageNeverPull` / `ErrImagePull` alırsın. Deploy script'i node'u tespit edip imajı kendisi aktarır; elle yapmak istersen:

```powershell
# Node container'ı görünür olmalı:
# Settings > Kubernetes > Show system containers (advanced) > Apply
docker save courier-tracking-api:local -o api.tar
docker cp api.tar desktop-control-plane:/api.tar
docker exec desktop-control-plane ctr -n k8s.io images import /api.tar
```

`docker save ... | docker exec -i ... ctr images import -` şeklinde **pipe kullanma**; PowerShell binary akışı bozar (`archive/tar: invalid tar header`).

Aynı modda NodePort host'a yayınlanmaz (`localhost:30080` bağlanmaz). Port-forward kullan:

```powershell
kubectl -n courier-tracking port-forward svc/courier-frontend 18080:80
```

Yerel HTTP registry (`localhost:5000`) bu modda işe yaramaz: çekme istekleri Docker Desktop'ın `registry-mirror`'ından geçer ve `500 Internal Server Error` döner.

### İlk kullanıcı

Cluster'daki Postgres boş başlar; Flyway sadece şemayı kurar, kullanıcı eklemez. Panelde giriş yapabilmek için önce kayıt ol:

```powershell
$b = @{ fullName="Berk Mermer"; email="berk@example.com"; phoneNumber="+905551112233"; password="securePass123" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:18080/api/v1/auth/register -ContentType "application/json" -Body $b
```

`curl.exe` ile `-d "{\"...\"}"` denemeyin; PowerShell 5.1 tırnakları bozar ve `JSON parse error` alırsınız.

### Temizlik

```powershell
kubectl delete -k k8s/overlays/local
# kind kullandıysan: kind delete cluster --name courier
```

---
## Kullanım

### Swagger demo akışı

1. `POST /api/v1/auth/register` — müşteri  
2. `POST /api/v1/auth/login` — JWT al → Authorize  
3. `POST /api/v1/orders` — pickup lat/lng ile sipariş  
4. Kurye JWT ile `PUT /api/v1/couriers/location`  
5. `POST /api/v1/orders/{id}/assign-courier`  
6. http://localhost:3000 üzerinde haritayı izle  

> Kurye hesabı seed ile gelir; public courier-register yok.

### Ekran görüntüleri

![Giriş](docs/screenshots/login.png)

![Canlı takip](docs/screenshots/live-tracking.png)

![Sipariş paneli](docs/screenshots/order-panel.png)

![Swagger](docs/screenshots/swagger.png)

---

## Frontend

React + Leaflet canlı takip paneli (Compose ile `:3000`):

- Açık harita (CARTO Voyager) + OSRM yol rotası  
- Motor ikonu, alış noktası, mesafe / ETA  
- Sipariş geçmişi (`GET /orders/me`)  

Manuel:

```bash
cd frontend
npm install
npm run dev
```

---

## Proje Yapısı

```
Live-Courier-Tracking/
├── src/main/java/com/berk/courier_tracking_api/
│   ├── config/          # Redis, Swagger, WebSocket (RabbitMQ relay)
│   ├── controller/      # Auth, Order, Courier
│   ├── dto/ entity/ enums/ exception/ repository/
│   ├── security/        # JWT filter, WS auth
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

---

## Güvenlik

| Konu | Önlem |
|------|--------|
| Auth | JWT (HS256), stateless |
| BOLA | Serviste sahiplik kontrolü |
| Rol | Kayıtta sabit CUSTOMER |
| Şifre | BCrypt |
| Soft delete | `deleted_at` + `@SQLRestriction` |
| CORS | Allowlist (`app.cors.allowed-origins`) |
| Probe | `/actuator/health` public; diger actuator kapali |

---

## Test

```bash
./mvnw test      # Linux / macOS
mvnw.cmd test    # Windows
```

---

## Geliştirici

**Berk Mermer** · [GitHub](https://github.com/BerkMermer)

---

## Lisans

Eğitim / portfolyo amaçlıdır. Ticari kullanım için proje sahibine sorun.
