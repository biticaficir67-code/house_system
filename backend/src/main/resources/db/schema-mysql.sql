CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    phone VARCHAR(32),
    role VARCHAR(32) NOT NULL DEFAULT 'AGENT',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS owner (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    id_number VARCHAR(64),
    remark VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS house (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT,
    title VARCHAR(128) NOT NULL,
    city VARCHAR(32) NOT NULL,
    district VARCHAR(32),
    community_name VARCHAR(128) NOT NULL,
    business_area VARCHAR(128),
    building_area_sqm DECIMAL(10,2) NOT NULL,
    rooms SMALLINT NOT NULL,
    halls SMALLINT NOT NULL DEFAULT 0,
    bathrooms SMALLINT NOT NULL DEFAULT 0,
    floor_level VARCHAR(16),
    total_floors SMALLINT,
    orientation JSON NOT NULL,
    decoration VARCHAR(16),
    building_type VARCHAR(32),
    has_elevator BOOLEAN,
    property_usage VARCHAR(32),
    listing_price_yuan DECIMAL(16,2),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    core_selling TEXT,
    community_intro TEXT,
    surrounding TEXT,
    analysis_summary VARCHAR(2000),
    analysis_advantages JSON,
    analysis_disadvantages JSON,
    analysis_word_freq JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_house_status_created (status, created_at),
    INDEX idx_house_community (community_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32),
    budget_min_yuan DECIMAL(16,2),
    budget_max_yuan DECIMAL(16,2),
    desired_districts JSON,
    desired_communities JSON,
    rooms_min SMALLINT,
    rooms_max SMALLINT,
    tags JSON,
    assigned_agent_id BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    remark TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS house_show (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    house_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    appointment_at DATETIME NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED',
    feedback TEXT,
    follow_up_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_house_show_agent_time (agent_id, appointment_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
