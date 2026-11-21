# Анализ тональности на Spring Boot

Приложение определяет тональность текста. В ответ приходит `positive`, `negative` или `neutral`: положительный, отрицательный или нейтральный текст. Проект сделан для курса «Оркестрация и контейнеризация».

Код написан на Java 21 и Spring Boot 3.5.16. Для анализа используется наивный байесовский классификатор. При запуске он обучается на 36 примерах на русском и английском языках и считает частоты слов. База данных не используется.

Это учебная модель. Она может ошибаться на отрицаниях и иронии. Если знакомых слов нет, возвращается `neutral`. Точность на отдельной выборке не проверялась.

## Запуск в Docker

Запустите Docker Desktop. Когда он загрузится, откройте PowerShell и скачайте проект:

```powershell
git clone https://github.com/ArtHouse17/MEPHI-Java-Kubernetes-Sentiment.git
cd MEPHI-Java-Kubernetes-Sentiment
```

Если проект уже скачан, эти две команды можно пропустить. Дальше работайте в его папке.

Соберите образ и запустите контейнер:

```powershell
docker build -t sentiment-spring:1.0.0 .
docker run --rm -p 8080:8080 -p 8081:8081 sentiment-spring:1.0.0
```

Java и Maven уже есть в Docker-образе. Контейнер работает в этом окне PowerShell, поэтому оставьте его открытым.

## Проверка приложения

Откройте в браузере [http://localhost:8080/api/sentiment?text=hello](http://localhost:8080/api/sentiment?text=hello).

Приложение вернёт:

```json
{"sentiment":"positive"}
```

Свой текст можно передать в параметре `text` или через POST на `/api/sentiment` с JSON:

```json
{"text":"отличный результат"}
```

Метрики можно посмотреть по адресу [http://localhost:8081/actuator/prometheus](http://localhost:8081/actuator/prometheus), а состояние приложения по адресу [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health).

Чтобы остановить контейнер, нажмите Ctrl+C в его окне PowerShell.

## Запуск в Kubernetes

В Kubernetes приложение запускается в трёх репликах. При росте нагрузки HPA может увеличить их число до шести. Доступ к приложению настроен через LoadBalancer и Traefik Ingress. Метрики собирает Prometheus, а Grafana показывает их на графиках.

Команды для Minikube есть в [MINIKUBE.md](MINIKUBE.md). Перед запуском в Kubernetes остановите контейнер Docker через Ctrl+C. Это освободит порт 8080.

## Файлы

| Папка или файл | Назначение |
| --- | --- |
| `src/main` | Код приложения и обучающие данные |
| `src/test` | Автоматические проверки API |
| `Dockerfile` | Сборка образа и запуск приложения |
| `k8s` | Манифесты Kubernetes |
| `helm` | Настройки Traefik, Prometheus и Grafana |
| `grafana/sentiment.json` | Дашборд приложения |
| `load` | Нагрузочный тест k6 |
