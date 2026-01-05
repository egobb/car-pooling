COMPOSE = docker compose -f deploy/docker-compose.yml
MVN = ./mvnw -f app/pom.xml

.PHONY: help up down logs ps run test fmt lint build docker-build docker-run

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*##' $(MAKEFILE_LIST) | awk 'BEGIN {FS=":.*##"} {printf "%-15s %s\n", $$1, $$2}'

up: ## Start the service with Docker Compose
	$(COMPOSE) up -d --build

down: ## Stop and remove containers
	$(COMPOSE) down -v

ps: ## List running containers
	$(COMPOSE) ps

logs: ## Tail logs
	$(COMPOSE) logs -f

run: ## Run locally (Spring Boot)
	$(MVN) -q spring-boot:run

build: ## Build jar (no tests)
	$(MVN) -q -B -DskipTests package

test: ## Run tests
	$(MVN) -q -B test

fmt: ## Format code
	$(MVN) -q spotless:apply

lint: ## Check formatting + compile (no tests)
	$(MVN) -q -B -DskipTests verify

docker-build: ## Build Docker image
	docker build -f deploy/Dockerfile -t car-pooling:local .

docker-run: ## Run Docker image
	docker run --rm -p 8080:8080 car-pooling:local
