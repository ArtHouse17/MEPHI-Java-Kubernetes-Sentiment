# Анализ тональности на Spring Boot

Учебное приложение по дисциплине «Оркестрация и контейнеризация». Принимает текст через REST API и возвращает его тональность: `positive`, `negative` или `neutral`.

Приложение написано на Java 21 и Spring Boot 3.5.16. Модель использует наивный байесовский классификатор и обучается при запуске на 36 примерах на русском и английском языках. Она считает частоты слов, поэтому может ошибаться на отрицаниях и иронии. Точность на отдельной выборке не проверялась. Если знакомых слов нет, ответом будет `neutral`. База данных не используется.

## Запуск в Docker

Откройте Docker Desktop и дождитесь его запуска. Затем откройте PowerShell и скачайте проект:

```powershell
git clone https://github.com/ArtHouse17/MEPHI-Java-Kubernetes-Sentiment.git
cd MEPHI-Java-Kubernetes-Sentiment
```

Если проект уже скачан, откройте PowerShell в его папке и пропустите эти две команды.

Соберите образ и запустите контейнер:

```powershell
docker build -t sentiment-spring:1.0.0 .
docker run --rm -p 8080:8080 -p 8081:8081 sentiment-spring:1.0.0
```

Java и Maven внутри образа уже есть. Отдельно устанавливать их для этого запуска не нужно. Терминал с контейнером оставьте открытым.

## Проверка приложения

Откройте в браузере [http://localhost:8080/api/sentiment?text=hello](http://localhost:8080/api/sentiment?text=hello).

Ожидаемый ответ:

```json
{"sentiment":"positive"}
```

Параметр `text` можно заменить своим текстом. API также принимает POST на `/api/sentiment` с JSON:

```json
{"text":"отличный результат"}
```

Метрики доступны по адресу [http://localhost:8081/actuator/prometheus](http://localhost:8081/actuator/prometheus), состояние приложения — [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health).

Для остановки контейнера нажмите **Ctrl+C** в его терминале.

## Запуск в Kubernetes

В проекте подготовлены три реплики приложения, доступ через LoadBalancer и Traefik Ingress, масштабирование HPA от трёх до шести реплик, а также мониторинг Prometheus и Grafana.

Порядок запуска описан в [MINIKUBE.md](MINIKUBE.md). Перед переходом к нему остановите контейнер Docker, чтобы освободить порт 8080.

## Файлы проекта

| Папка или файл | Назначение |
| --- | --- |
| `src/main` | Код приложения и обучающие данные |
| `src/test` | Автоматические проверки API |
| `Dockerfile` | Сборка образа и запуск приложения |
| `k8s` | Манифесты Kubernetes |
| `helm` | Настройки Traefik, Prometheus и Grafana |
| `grafana/sentiment.json` | Дашборд приложения |
| `load` | Нагрузочный тест k6 |
