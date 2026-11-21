# Анализ тональности на Spring Boot

Отдельное Java-приложение с REST API. Используются Spring Boot 3.5.16, Spring MVC, Actuator и Micrometer Prometheus. Для сборки и запуска нужна Java 21 или новее.

Модель обучается при запуске на 36 размеченных примерах на русском и английском из `src/main/resources/training.tsv`. Мультиномиальный наивный байесовский классификатор использует частоты слов и сглаживание Лапласа. Модель хранится в памяти, после обучения запросы только читают её состояние. Классы ответа — `positive`, `negative` и `neutral`.

Корпус учебный. Если известных слов нет, ответом будет `neutral`. Отрицания, ирония и формы слов могут давать ошибки. Качество на реальных данных нужно оценивать на отдельной размеченной выборке.

## Сборка и запуск на вашем ПК

Откройте PowerShell в корне проекта. На проверенном ПК установлен JDK 23 по следующему пути. Переменная действует в текущей вкладке PowerShell.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-23'
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd clean package
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\sentiment-spring.jar
```

Maven Wrapper скачает указанную в проекте версию Maven при первом обращении. Maven скачивает зависимости из Maven Central. Остановить сервер можно через Ctrl+C.

Для запуска из исходников с автоматической сборкой:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-23'
.\mvnw.cmd spring-boot:run
```

В IntelliJ IDEA откройте `pom.xml` как проект Maven, выберите JDK 21+ и запустите `SentimentApplication.main`.

## Запросы

После запуска откройте новую вкладку PowerShell.

```powershell
curl.exe -i "http://localhost:8080/api/sentiment?text=hello"
```

Ответ с HTTP 200:

```json
{"sentiment":"positive"}
```

В заголовке `X-Pod-Name` содержится `local`. Если задана переменная окружения `POD_NAME`, в заголовке будет её значение.

Для JSON и длинных текстов используйте POST. PowerShell сам кодирует кириллицу в UTF-8.

```powershell
$payload = @{ text = 'отличный результат' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/sentiment' -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($payload))
```

Примеры для проверки:

| Текст | Ожидаемый класс |
| --- | --- |
| hello | positive |
| awful service | negative |
| the meeting starts at noon | neutral |
| отличный результат | positive |
| ужасно плохое качество | negative |
| встреча начинается в полдень | neutral |

Текст должен содержать хотя бы один непробельный символ и занимать не более 4000 кодовых единиц UTF-16, как `String.length()` в Java. Пустой текст, отсутствующее поле, превышение длины и повреждённый JSON возвращают HTTP 400. Сервер возвращает ошибки в формате Problem Details. Для неизвестных слов, цифр и пунктуации результатом будет `neutral`.

## Здоровье и метрики

API слушает порт 8080, Actuator — 8081.

```powershell
curl.exe -fsS http://localhost:8080/livez
curl.exe -fsS http://localhost:8080/readyz
curl.exe -fsS http://localhost:8081/actuator/health
curl.exe -fsS http://localhost:8081/actuator/prometheus
```

В Prometheus задайте путь `/actuator/prometheus` и порт `8081`. После запросов к API появляются `http_server_requests_seconds_count` и гистограмма `http_server_requests_seconds_bucket`, пригодная для вычисления p95. Также доступны метрики JVM и процесса.

Если порты заняты, их можно изменить при запуске:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\sentiment-spring.jar --server.port=18080 --management.server.port=18081
```

## Docker

Dockerfile использует один образ Maven с Java 21. В рабочую папку /app копируются pom.xml и исходники, команда mvn package собирает JAR, а java -jar запускает приложение.

```powershell
docker build -t sentiment-spring:1.0.0 .
if ($LASTEXITCODE -ne 0) { throw 'Docker build failed' }
docker image inspect sentiment-spring:1.0.0 --format '{{.Size}}'
docker run --rm --memory=384m -p 127.0.0.1:8080:8080 -p 127.0.0.1:8081:8081 sentiment-spring:1.0.0
```

Размер в байтах нужно проверить после сборки. В задании граница составляет 150 000 000 байт.

## Перенос в Minikube

В проект включены Deployment с тремя репликами, Service LoadBalancer, Ingress для Traefik, HPA по CPU, ServiceMonitor, настройки Helm и дашборд Grafana. Образ для манифеста — `sentiment-spring:1.0.0`.

Порядок переноса, команды проверки и мониторинга находятся в [MINIKUBE.md](MINIKUBE.md). Там же описаны повторное развёртывание новой версии и откат.

Модель обучается на файле `training.tsv` при запуске каждого экземпляра и хранится в памяти. У проекта отсутствуют подключения к БД, таблицы и файлы миграций Flyway/Liquibase.

## Структура

| Файл | Назначение |
| --- | --- |
| `SentimentApplication.java` | Запуск Spring Boot и поиск компонентов. |
| `SentimentController.java` | GET и POST, валидация, HTTP-ответ и заголовок имени экземпляра. |
| `SentimentModel.java` | Токенизация, обучение и классификация. |
| `application.yaml` | Порты, health probes, Problem Details и метрики. |
| `training.tsv` | По одной паре «класс — текст» на строку, разделитель — табуляция. |
| `SentimentApiTest.java` | Проверка классификации, JSON и граничных значений ввода. |

Документация проекта Spring Boot описывает [публикацию health и Prometheus](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html). Для сборки используется [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/).
