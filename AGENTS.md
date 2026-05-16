# AGENTS.md

## Repository Structure

This is a monorepo with:
- `backend/` - Quarkus Java backend with Maven build
- `frontend/` - Nuxt.js frontend 
- `infrastructure/` - Docker Compose deployment configs
- `e2e/` - Playwright end-to-end tests

## Key Commands

### Development Setup
```bash
# Start all services (including databases, keycloak, backend, frontend)
npm run start:all

# Start backend in dev mode
npm run start:backend

# Start frontend in dev mode  
npm run start:frontend

# Start both backend and frontend
npm run start:dev
```

### Testing
```bash
# Run Playwright tests (requires keycloak running)
cd e2e && npx playwright test

# Run specific test file
cd e2e && npx playwright test auth.spec.ts

# Run tests with GUI
cd e2e && npx playwright test --ui
```

### Build & Deployment
```bash
# Build backend with Maven
cd backend && ./mvnw package -DskipTests

# Build frontend 
cd frontend && npm run build

# Build Docker images
cd backend && docker build -t shepard-backend .
```

## Important Facts

### Authentication
- Keycloak is configured for OIDC authentication using realm `shepard-demo`
- Users: alice/alice-demo, bob/bob-demo, admin/admin-demo, harvester/harvester-demo
- Backend requires `OIDC_AUTHORITY`, `OIDC_PUBLIC`, `CLIENT_ID` environment variables
- Keycloak is exposed on port 8082 (`http://localhost:8082`)

### Docker Compose
- Main compose file: `infrastructure/docker-compose.yml`
- Services include: backend, frontend, keycloak, neo4j, mongodb, timescaledb, caddy
- Profiles available: `monitoring`, `frontend-old`, `spatial`, `hdf`, `timescale-migration-preparation`
- To run with monitoring: `docker compose --profile monitoring up -d`

### Backend Architecture
- Built with Quarkus framework
- Uses Flyway for database migrations
- Uses Maven with `./mvnw` wrapper (not system Maven)
- Main application entrypoint: `backend/src/main/java/de/dlr/shepard/ShepardMain.java`
- Environment configuration via `.env` file in root or `infrastructure/.env`

### Frontend Architecture
- Built with Nuxt 3
- Uses TypeScript
- Authentication via Nuxt's `@nuxtjs/oidc` module
- UI built with Vuetify 3

### Database
- Neo4j for graph storage (7687)
- MongoDB for file storage (27017) 
- TimescaleDB for time-series data (5432)
- PostgreSQL for relational data (5432)
- PostGIS for spatial data (5432)

### Authentication Flow
1. User navigates to frontend at port 3000
2. Redirects to Keycloak at port 8082 for authentication
3. After successful login, Keycloak redirects back to frontend
4. JWT token is exchanged for backend session

### Environment Requirements
- Node.js 21+ (for frontend development)
- Java 21+ (for backend development)
- Docker (for full deployment)

### Migration Issues
The backend service currently has Flyway migration validation issues (1.8.0 and 1.9.0) that prevent it from starting. This blocks full authentication from functioning properly.

### Playwright Tests
- Located in `e2e/tests/` 
- Base URL defaults to `https://shepard.nuclide.systems` 
- Requires Keycloak on `http://192.168.1.49:8082` by default
- Tests cover authentication flows with users alice, bob, admin, harvester