# payments-processing

Заглушка процессинга: переводы (в том числе долгие), справочник СБП, входящие СБП, перечисления фондам.

- Порт: `8085`, Swagger UI — http://localhost:8085/swagger-ui.html
- Зависит от: Redis (итоги переводов), payment (уведомления)
- Общая библиотека — `com.paymentsapp:platform` (версия — `platform.version` в `pom.xml`), скачивается из GitHub Packages
- Образ: `ghcr.io/payments-app-org/payments-processing:<sha коммита main>`

## Сборка и тесты

Unit — `mvn test`, интеграционные `*IT` на Testcontainers — `mvn verify` (нужен Docker). Java ставить не нужно:

```bash
docker run --rm -v "$PWD":/src -v payments-m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e GITHUB_TOKEN=$(gh auth token) \
  -w /src maven:3.9-eclipse-temurin-21 mvn -B -s .mvn/settings.xml verify
```

Запустить вместе с остальными — из `payments-local`: `scripts/up.sh processing`.

## CI

`.github/workflows/ci.yml` — шаблон `java-service.yml` из [payments-ci](https://github.com/payments-app-org/payments-ci): тесты → проверка зависимостей → образ и его проверка; в `main` — публикация образа. Слияние в `main` — только при зелёной проверке `ci / ok`.

Устройство всего приложения — [payments-local/CLAUDE.md](https://github.com/payments-app-org/payments-local/blob/main/CLAUDE.md).
