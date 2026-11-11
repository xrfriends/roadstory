-- 기존 PostgreSQL 서버에 Road Story DB와 로그인 계정을 최초 생성한다.
-- 실행 (저장소 루트에서, 관리자 계정으로):
--   psql -X -h localhost -p 5432 -U postgres -d postgres -f scripts/db/create-roadstory.sql
-- 서버에 PostGIS 패키지가 설치되어 있어야 한다. PostgreSQL 설치는 수행하지 않는다.
-- 비밀번호는 실행 중 두 번 입력하며 파일이나 명령줄에 기재하지 않는다.
-- CREATE DATABASE는 트랜잭션 안에서 실행할 수 없으므로 -1 옵션을 사용하지 않는다.
-- 기존 roadstory DB/계정이 있으면 변경하지 않고 중단한다.
-- 중간 실패 시 앞서 생성한 리소스가 남을 수 있으므로 관리자 확인 후 재실행한다.
-- 관련 요구사항: REQ-CMS-008 (수집 데이터 저장 기반). Screen/Flow: 해당 없음.
-- 애플리케이션 테이블은 생성하지 않는다. backend의 Flyway migration으로 관리한다.
-- 현재 local 프로필은 Fake Repository를 사용하며 Flyway가 비활성화되어 있다.

\set ON_ERROR_STOP on

-- 충돌 및 필수 확장을 변경 전에 확인한다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'roadstory') THEN
        RAISE EXCEPTION 'Role roadstory already exists; no changes made.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_database WHERE datname = 'roadstory') THEN
        RAISE EXCEPTION 'Database roadstory already exists; no changes made.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'postgis') THEN
        RAISE EXCEPTION 'Install PostGIS on the PostgreSQL server before running this script.';
    END IF;
END
$$;

CREATE ROLE roadstory WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS;

-- psql의 비밀번호 입력 기능을 사용한다 (입력값 화면 표시 없음).
\password roadstory

CREATE DATABASE roadstory
    OWNER roadstory
    ENCODING 'UTF8'
    TEMPLATE template0;

REVOKE ALL ON DATABASE roadstory FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE roadstory TO roadstory;

-- 접속한 서버와 관리자 계정을 유지하고 새 DB로 전환한다.
\connect roadstory

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO roadstory;

-- 관리자 권한이 필요한 확장을 미리 생성한다 (V1 migration과 호환).
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;

-- Backend 설정:
-- DATABASE_URL=jdbc:postgresql://<host>:<port>/roadstory
-- DATABASE_USERNAME=roadstory
-- DATABASE_PASSWORD=<실행 중 입력한 비밀번호>
\echo 'Road Story database and role created.'
