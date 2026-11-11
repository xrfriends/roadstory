.PHONY: install dev backend web-harness frontend pleos-test pleos-fake pleos-sdk test contract e2e docker-up docker-down

install:
	cd frontend/web-harness && npm ci
	cd e2e && npm ci

dev:
	docker compose up -d
	@echo "backend: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'"
	@echo "web-harness: cd frontend/web-harness && npm run dev"

backend:
	cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'

web-harness:
	cd frontend/web-harness && npm run dev

frontend: web-harness

pleos-test:
	frontend/pleos-app/gradlew -p frontend/pleos-app testFakeDebugUnitTest

pleos-fake:
	frontend/pleos-app/gradlew -p frontend/pleos-app assembleFakeDebug

pleos-sdk:
	frontend/pleos-app/gradlew -p frontend/pleos-app assemblePleosDebug

contract:
	cd frontend/web-harness && npm run generate:api

test:
	cd backend && ./gradlew test
	cd frontend/web-harness && npm run lint && npm run test && npm run build
	frontend/pleos-app/gradlew -p frontend/pleos-app testFakeDebugUnitTest assembleFakeDebug

e2e:
	cd e2e && npx playwright test

docker-up:
	docker compose up -d

docker-down:
	docker compose down
