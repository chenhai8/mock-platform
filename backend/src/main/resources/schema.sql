-- MySQL 5.7 / 8.x 初始化脚本，由 Spring Boot 连接目标数据库后自动执行
-- 仅创建缺失的表，不删除已有数据，也不自动修改已有表结构
CREATE TABLE IF NOT EXISTS mock_system
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    system_key  VARCHAR(40)  NOT NULL UNIQUE,
    description VARCHAR(500) NOT NULL DEFAULT '',
    sort_order  INT          NOT NULL DEFAULT 0,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    archived    BOOLEAN      NOT NULL DEFAULT FALSE,
    revision    BIGINT       NOT NULL DEFAULT 0,
    created_at  VARCHAR(40)  NOT NULL,
    updated_at  VARCHAR(40)  NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
CREATE TABLE IF NOT EXISTS mock_endpoint
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id         BIGINT      NOT NULL,
    draft_json        LONGTEXT    NOT NULL,
    published_json    LONGTEXT,
    revision          BIGINT      NOT NULL DEFAULT 0,
    published_version INT         NOT NULL DEFAULT 0,
    archived          BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        VARCHAR(40) NOT NULL,
    updated_at        VARCHAR(40) NOT NULL,
    INDEX idx_endpoint_system (system_id, archived),
    CONSTRAINT fk_endpoint_system FOREIGN KEY (system_id) REFERENCES mock_system (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
CREATE TABLE IF NOT EXISTS mock_version
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    endpoint_id    BIGINT      NOT NULL,
    version        INT         NOT NULL,
    config_json    LONGTEXT    NOT NULL,
    action         VARCHAR(20) NOT NULL,
    source_version INT,
    created_at     VARCHAR(40) NOT NULL,
    CONSTRAINT uq_endpoint_version UNIQUE (endpoint_id, version),
    CONSTRAINT fk_version_endpoint FOREIGN KEY (endpoint_id) REFERENCES mock_endpoint (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
CREATE TABLE IF NOT EXISTS mock_call_log
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(40) NOT NULL,
    system_id       BIGINT,
    endpoint_id     BIGINT,
    system_name     VARCHAR(50),
    source          VARCHAR(20) NOT NULL,
    request_method  VARCHAR(10),
    request_path    VARCHAR(1000),
    config_version  BIGINT,
    scene           VARCHAR(50),
    status          INT         NOT NULL,
    error_code      VARCHAR(60),
    duration_ms     BIGINT,
    queue_ms        BIGINT,
    script_ms       BIGINT,
    delay_ms        INT,
    request_bytes   INT,
    response_bytes  INT,
    source_address  VARCHAR(100),
    request_detail  LONGTEXT,
    response_detail LONGTEXT,
    created_at      VARCHAR(40) NOT NULL,
    INDEX idx_log_created (created_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
CREATE TABLE IF NOT EXISTS mock_operation
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type    VARCHAR(20) NOT NULL,
    target_id      BIGINT,
    action         VARCHAR(30) NOT NULL,
    detail         VARCHAR(1000),
    source_address VARCHAR(100),
    created_at     VARCHAR(40) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
