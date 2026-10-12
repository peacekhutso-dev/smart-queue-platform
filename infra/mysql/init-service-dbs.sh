#!/bin/sh
# Each extracted service gets its own schema (not the shared smart_queue
# schema) on this same MySQL instance, per the microservice migration plan's
# "schema-per-service, scoped credentials, no cross-schema grants" rule.
# Only payment-service gets a fully separate MySQL instance; everything else
# shares this one safely because each service's DB user can only see its
# own schema. Runs once, on first init of an empty data volume.
# Passwords come from .env (passed in by docker-compose.yml), never this file.
set -eu

mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE DATABASE IF NOT EXISTS identity_db;
CREATE USER IF NOT EXISTS 'identity_user'@'%' IDENTIFIED BY '${IDENTITY_DB_PASS}';
GRANT ALL PRIVILEGES ON identity_db.* TO 'identity_user'@'%';

CREATE DATABASE IF NOT EXISTS branch_db;
CREATE USER IF NOT EXISTS 'branch_user'@'%' IDENTIFIED BY '${BRANCH_DB_PASS}';
GRANT ALL PRIVILEGES ON branch_db.* TO 'branch_user'@'%';

CREATE DATABASE IF NOT EXISTS queue_db;
CREATE USER IF NOT EXISTS 'queue_user'@'%' IDENTIFIED BY '${QUEUE_DB_PASS}';
GRANT ALL PRIVILEGES ON queue_db.* TO 'queue_user'@'%';

CREATE DATABASE IF NOT EXISTS document_db;
CREATE USER IF NOT EXISTS 'document_user'@'%' IDENTIFIED BY '${DOCUMENT_DB_PASS}';
GRANT ALL PRIVILEGES ON document_db.* TO 'document_user'@'%';

CREATE DATABASE IF NOT EXISTS feedback_db;
CREATE USER IF NOT EXISTS 'feedback_user'@'%' IDENTIFIED BY '${FEEDBACK_DB_PASS}';
GRANT ALL PRIVILEGES ON feedback_db.* TO 'feedback_user'@'%';

CREATE DATABASE IF NOT EXISTS catalog_db;
CREATE USER IF NOT EXISTS 'catalog_user'@'%' IDENTIFIED BY '${CATALOG_DB_PASS}';
GRANT ALL PRIVILEGES ON catalog_db.* TO 'catalog_user'@'%';

FLUSH PRIVILEGES;
SQL
