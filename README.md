# Courier Tracking API

> Gerçek zamanlı kurye konum takibi, Redis GEO ile akıllı atama ve STOMP ile canlı izleme.  
> **Staj / portfolio demosu** — production ürünü değil; bilinçli olarak dar tutulmuş bir öğrenme projesidir.

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)](https://redis.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-STOMP-FF6600?logo=rabbitmq)](https://www.rabbitmq.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://www.docker.com/)

---

## İçindekiler

- [Proje Hakkında](#proje-hakkında)
- [Sistem Mimarisi](#sistem-mimarisi)
- [Teknoloji Stack](#teknoloji-stack)
- [Özellikler](#özellikler)
- [API Endpoint’leri](#api-endpointleri)
- [Kurulum (Docker)](#kurulum-docker)
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

### Bilinçli kapsam dışı

| Konu | Not |
|------|-----|
| Misafir sipariş | Auth zorunlu |
| Kurye self-register | Seed / SQL ile `COURIER` |
| AI chat | Kaldırıldı |
| Kubernetes / k8s | Bu repoda yok (Compose yeterli) |
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
| Test / ops | JUnit, Mockito, Testcontainers, Docker Compose |

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
├── frontend/            # React + Vite + Leaflet
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
