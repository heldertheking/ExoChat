-- Runs once, on first start of an empty data volume.
CREATE DATABASE IF NOT EXISTS exochat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS exochat_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'exochat'@'%' IDENTIFIED BY 'exoch4t';
GRANT ALL PRIVILEGES ON exochat.* TO 'exochat'@'%';
GRANT ALL PRIVILEGES ON exochat_test.* TO 'exochat'@'%';
FLUSH PRIVILEGES;
