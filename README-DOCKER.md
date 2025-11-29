# Docker Deployment Guide

This guide explains how to deploy the Intact ID Auth Server using Docker and Docker Compose.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+

## Quick Start

### 1. Environment Configuration

Create a `.env` file from the example:

```bash
cp .env.example .env
```

Edit `.env` and set your secure values:

```env
DB_PASSWORD=your_secure_database_password
JWT_SECRET=your_secure_jwt_secret_key_minimum_256_bits
SPRING_PROFILES_ACTIVE=prod
```

### 2. Build and Run

**Development/Local:**

```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

**Production/VPS:**

```bash
# Pull and start (if image is pre-built and pushed to registry)
docker-compose pull
docker-compose up -d

# Or build and start
docker-compose up -d --build
```

### 3. Access the Application

- **API Base URL:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **API Docs:** http://localhost:8080/v3/api-docs

## Docker Commands

### Building

```bash
# Build the application image
docker-compose build

# Build without cache
docker-compose build --no-cache
```

### Running

```bash
# Start services in detached mode
docker-compose up -d

# Start and rebuild
docker-compose up -d --build

# View logs
docker-compose logs -f auth-server
docker-compose logs -f postgres
```

### Stopping

```bash
# Stop services (keeps volumes)
docker-compose stop

# Stop and remove containers (keeps volumes)
docker-compose down

# Stop and remove everything including volumes
docker-compose down -v
```

### Maintenance

```bash
# Restart a specific service
docker-compose restart auth-server

# Execute commands in running container
docker-compose exec auth-server sh

# Access PostgreSQL
docker-compose exec postgres psql -U postgres -d intact_id

# View container status
docker-compose ps
```

## Pushing to Docker Registry

To deploy on your VPS, push the image to a Docker registry:

### Using Docker Hub

```bash
# Login to Docker Hub
docker login

# Tag the image
docker tag intact-id-auth-server:latest your-username/intact-id-auth-server:latest

# Push to registry
docker push your-username/intact-id-auth-server:latest
```

### Using Private Registry

```bash
# Tag for private registry
docker tag intact-id-auth-server:latest registry.yourdomain.com/intact-id-auth-server:latest

# Push to private registry
docker push registry.yourdomain.com/intact-id-auth-server:latest
```

### Update docker-compose.yml on VPS

Replace the `build` section with `image`:

```yaml
auth-server:
  image: your-username/intact-id-auth-server:latest
  # Remove the build section
```

## VPS Deployment

### 1. On Your Development Machine

```bash
# Build and push image
docker-compose build
docker tag intact-id-auth-server:latest your-username/intact-id-auth-server:latest
docker push your-username/intact-id-auth-server:latest
```

### 2. On Your VPS

```bash
# Clone or copy docker-compose.yml and .env
# Then pull and run
docker-compose pull
docker-compose up -d
```

## Database Backup and Restore

### Backup

```bash
# Create backup
docker-compose exec postgres pg_dump -U postgres intact_id > backup.sql

# Or with docker command
docker exec intact-id-postgres pg_dump -U postgres intact_id > backup.sql
```

### Restore

```bash
# Restore from backup
docker-compose exec -T postgres psql -U postgres intact_id < backup.sql
```

## Troubleshooting

### Check Service Health

```bash
docker-compose ps
docker-compose logs auth-server
```

### Database Connection Issues

```bash
# Check if PostgreSQL is ready
docker-compose exec postgres pg_isready -U postgres

# Check database exists
docker-compose exec postgres psql -U postgres -l
```

### Application Not Starting

```bash
# Check logs
docker-compose logs -f auth-server

# Check environment variables
docker-compose exec auth-server env | grep -E 'DB_|JWT_'
```

### Reset Everything

```bash
# Stop and remove all containers, networks, and volumes
docker-compose down -v

# Start fresh
docker-compose up -d --build
```

## Security Recommendations

1. **Change Default Passwords:** Always use strong, unique passwords in production
2. **Secure JWT Secret:** Use a cryptographically secure random string (minimum 256 bits)
3. **Use HTTPS:** Configure a reverse proxy (nginx/traefik) with SSL/TLS certificates
4. **Firewall Rules:** Only expose necessary ports
5. **Regular Updates:** Keep Docker images and dependencies updated
6. **Environment Variables:** Never commit `.env` file to version control

## Performance Tuning

### Adjust JVM Memory

Edit `docker-compose.yml`:

```yaml
environment:
  JAVA_OPTS: "-Xmx1024m -Xms512m"
```

### Database Connection Pool

Add to `application.yaml`:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
```

## Monitoring

### Health Checks

- Application: http://localhost:8080/actuator/health
- Database: `docker-compose exec postgres pg_isready`

### Resource Usage

```bash
# Check resource usage
docker stats

# Check specific container
docker stats intact-id-auth-server
```
