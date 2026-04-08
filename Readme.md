# Order Processing Platform

## Требования

- Docker Desktop (WSL 2), ≥ 2 CPU / 8 GB RAM
- GNU Make
- k3d — для Kubernetes

## Запуск

```bash
cp .secrets/dev/.env.example .secrets/dev/.env    # заполнить значения
make dev-up                                       # или ./dev-up.ps1
```

## Проверка health

```bash
for port in 8080 8081 8082 8083 8084 8085 8086; do
  printf ":%s → " "$port"
  curl -sf "http://localhost:$port/actuator/health" | grep -o '"status":"[^"]*"' || echo "FAIL"
done
```

| Сервис               | Порт | gRPC |
|----------------------|------|------|
| api-gateway          | 8080 | —    |
| auth-service         | 8081 | —    |
| user-service         | 8082 | —    |
| product-service      | 8083 | —    |
| inventory-service    | 8084 | 9094 |
| order-service        | 8085 | 9095 |
| notification-service | 8086 | —    |

## Остановка

```bash
make dev-down   # или ./dev-down.ps1
```

## Сборка

```bash
./mvnw clean install              # с тестами
./mvnw checkstyle:check           # проверка стиля (Google Style)
```

## Kubernetes (k3d)

```bash
make k3d-up
make inject-secrets
```