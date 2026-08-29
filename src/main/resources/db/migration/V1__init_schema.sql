-- =============================================================================
-- V1: Courier Tracking API — Initial Schema
-- PostgreSQL 14+
-- =============================================================================
-- Tablolar: users → courier_profiles → orders (FK bağımlılık sırası)
-- Enum alanları VARCHAR + CHECK constraint ile modellenmiştir (@Enumerated STRING).
-- Soft delete: deleted_at IS NULL aktif kayıt anlamına gelir (@SQLRestriction).
-- =============================================================================

-- ─── USERS ───────────────────────────────────────────────────────────────────

CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    full_name       VARCHAR(255)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    phone_number    VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    role            VARCHAR(50)     NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP,

    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'COURIER', 'ADMIN'))
);

CREATE UNIQUE INDEX idx_users_email ON users (email);
CREATE UNIQUE INDEX idx_users_phone ON users (phone_number);

COMMENT ON TABLE users IS 'Sistem kullanıcıları (müşteri, kurye, admin)';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash — düz metin şifre asla saklanmaz';
COMMENT ON COLUMN users.deleted_at IS 'Soft delete zaman damgası; NULL = aktif kayıt';

-- ─── COURIER PROFILES ────────────────────────────────────────────────────────

CREATE TABLE courier_profiles (
    id                      BIGSERIAL       PRIMARY KEY,
    user_id                 BIGINT          NOT NULL,
    phone_number            VARCHAR(255)    NOT NULL,
    vehicle_plate           VARCHAR(255)    NOT NULL,
    status                  VARCHAR(50)     NOT NULL,
    last_known_lat          DOUBLE PRECISION,
    last_known_lng          DOUBLE PRECISION,
    last_location_update    TIMESTAMP,
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP,
    deleted_at              TIMESTAMP,

    CONSTRAINT fk_courier_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT uq_courier_profiles_user_id UNIQUE (user_id),

    CONSTRAINT ck_courier_profiles_status
        CHECK (status IN ('AVAILABLE', 'ON_DELIVERY', 'OFFLINE'))
);

CREATE INDEX idx_courier_status ON courier_profiles (status);
CREATE INDEX idx_courier_phone ON courier_profiles (phone_number);

-- Partial index: sipariş atama sorgusu (WHERE status = 'AVAILABLE') için optimize edilmiş
CREATE INDEX idx_courier_status_available
    ON courier_profiles (status)
    WHERE status = 'AVAILABLE' AND deleted_at IS NULL;

COMMENT ON TABLE courier_profiles IS 'Kurye profil ve operasyonel durum bilgileri';
COMMENT ON COLUMN courier_profiles.last_known_lat IS 'PostgreSQL cache — anlık konum Redis GEO''da tutulur';
COMMENT ON COLUMN courier_profiles.last_location_update IS 'Son konum güncelleme zamanı';

-- ─── ORDERS ──────────────────────────────────────────────────────────────────

CREATE TABLE orders (
    id                  BIGSERIAL       PRIMARY KEY,
    tracking_number     VARCHAR(255)    NOT NULL,
    customer_id         BIGINT          NOT NULL,
    courier_id          BIGINT,
    pickup_address      VARCHAR(255)    NOT NULL,
    pickup_latitude     DOUBLE PRECISION NOT NULL,
    pickup_longitude    DOUBLE PRECISION NOT NULL,
    delivery_address    VARCHAR(255)    NOT NULL,
    status              VARCHAR(50)     NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP,

    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES users (id),

    CONSTRAINT fk_orders_courier
        FOREIGN KEY (courier_id) REFERENCES courier_profiles (id),

    CONSTRAINT ck_orders_status
        CHECK (status IN ('PENDING', 'ASSIGNED', 'PICKED_UP', 'DELIVERED', 'CANCELLED'))
);

CREATE INDEX idx_orders_status_created ON orders (status, created_at);
CREATE INDEX idx_orders_customer ON orders (customer_id);
CREATE INDEX idx_orders_courier ON orders (courier_id);
CREATE UNIQUE INDEX idx_orders_tracking ON orders (tracking_number);

COMMENT ON TABLE orders IS 'Kurye teslimat siparişleri';
COMMENT ON COLUMN orders.tracking_number IS 'UUID tabanlı dış takip numarası — id sızdırılmaz';
COMMENT ON COLUMN orders.courier_id IS 'NULL = henüz kurye atanmamış (PENDING)';
COMMENT ON COLUMN orders.pickup_latitude IS 'Alım noktası enlemi — Redis GEO eşleştirme için';
COMMENT ON COLUMN orders.pickup_longitude IS 'Alım noktası boylamı — Redis GEO eşleştirme için';
