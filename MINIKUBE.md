# Запуск приложения в Minikube

Нужны Docker Desktop, Minikube, kubectl и Helm. Команды выполняются в PowerShell из папки проекта. Docker-образ `sentiment-spring:1.0.0` должен быть собран по [README.md](README.md). Контейнер, запущенный для проверки Docker, перед этим остановите через **Ctrl+C**.

## 1. Запустить кластер

Откройте Docker Desktop. Затем выполните:

```powershell
minikube start -p sentiment-lab --driver=docker --cpus=4 --memory=4096
kubectl config use-context sentiment-lab
kubectl get nodes
```

В выводе должен появиться узел со статусом `Ready`. Здесь используется локальный кластер с одним узлом.

Включите сбор метрик CPU и загрузите образ приложения в Minikube:

```powershell
minikube addons enable metrics-server -p sentiment-lab
minikube image load sentiment-spring:1.0.0 -p sentiment-lab
```

## 2. Установить Traefik и запустить приложение

Traefik нужен для доступа через Ingress. Helm установит его или обновит существующую установку:

```powershell
helm repo add traefik https://traefik.github.io/charts
helm repo update
helm upgrade --install traefik traefik/traefik -n ingress --create-namespace -f helm/traefik-values.yaml
```

Примените манифесты приложения:

```powershell
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/app.yaml
kubectl get pods -n sentiment
```

Дождитесь, пока у трёх Pod будут значения `1/1` и `Running`. В манифестах уже настроены ресурсы контейнера, проверки состояния и HPA с диапазоном от трёх до шести реплик.

## 3. Проверить доступ к API

В отдельном окне PowerShell запустите туннель и оставьте его работать:

```powershell
minikube tunnel -p sentiment-lab
```

В основном окне посмотрите внешний адрес сервиса:

```powershell
kubectl get service sentiment -n sentiment
```

Используйте адрес из столбца `EXTERNAL-IP` и порт `8080`. Например, при адресе `127.0.0.1` откройте в браузере [http://127.0.0.1:8080/api/sentiment?text=hello](http://127.0.0.1:8080/api/sentiment?text=hello). Ожидается `{"sentiment":"positive"}`.

Для проверки Ingress откройте ещё одно окно PowerShell:

```powershell
kubectl port-forward service/traefik 18080:80 -n ingress
```

Оставьте его открытым. В основном окне выполните:

```powershell
curl.exe -i "http://localhost:18080/api/sentiment?text=hello"
```

Повторите запрос несколько раз. В ответах должны быть HTTP 200 и `positive`. Заголовок `X-Pod-Name` показывает, какой Pod обработал запрос. Разные имена означают, что запросы попадают в разные реплики.

## 4. Открыть Prometheus и Grafana

Установите мониторинг через Helm. Для этой учебной установки команда задаёт пароль Grafana `sentiment-demo`:

```powershell
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack -n monitoring --create-namespace -f helm/monitoring-values.yaml --set grafana.adminPassword=sentiment-demo --wait --timeout 10m
kubectl apply -f k8s/service-monitor.yaml
kubectl get pods -n monitoring
```

Дождитесь запуска компонентов мониторинга. В двух отдельных окнах PowerShell выполните по одной команде и оставьте их работать.

Для Prometheus:

```powershell
kubectl port-forward service/lab-monitoring-prometheus 19090:9090 -n monitoring
```

Для Grafana:

```powershell
kubectl port-forward service/lab-grafana 13000:80 -n monitoring
```

Откройте [Prometheus Targets](http://localhost:19090/targets). Найдите цель приложения `sentiment`: после успешного сбора метрик у неё должен быть статус `UP`.

Откройте [Grafana](http://localhost:13000). Логин — `admin`, пароль — `sentiment-demo`.

Загрузите готовый дашборд через интерфейс:

1. Откройте **Dashboards**, затем **New → Import dashboard**.
2. Загрузите файл `grafana/sentiment.json` из проекта.
3. Если появится выбор источника данных, выберите Prometheus.
4. Нажмите **Import**.

На дашборде появятся запросы в секунду, задержка p95, CPU по Pod и число реплик HPA. Данные начнут поступать после запросов к API или нагрузочного теста. [Документация импорта дашборда](https://grafana.com/docs/grafana/latest/visualizations/dashboards/build-dashboards/import-dashboards/).

## 5. Запустить нагрузочный тест

При первом запуске создайте ConfigMap с тестовым скриптом, затем запустите Job:

```powershell
kubectl create configmap sentiment-load-script --from-file=load/load.js -n sentiment
kubectl apply -f load/job.yaml
```

Тест использует 60 виртуальных пользователей и работает пять минут. В другом окне можно наблюдать за HPA:

```powershell
kubectl get hpa -n sentiment -w
```

Для выхода из наблюдения нажмите **Ctrl+C**. Это не останавливает сам тест.

Под нагрузкой проверьте число Pod и расход CPU:

```powershell
kubectl get pods -n sentiment
kubectl top pods -n sentiment
```

После завершения пяти минут посмотрите результаты:

```powershell
kubectl logs job/sentiment-load -n sentiment
```

HPA может увеличить число реплик до шести, если CPU превысит целевые 60%. После нагрузки число реплик постепенно вернётся к трём.

Для повторного теста удалите завершённый Job и запустите его снова. ConfigMap повторно создавать не нужно:

```powershell
kubectl delete job sentiment-load -n sentiment
kubectl apply -f load/job.yaml
```

## 6. Завершить работу

Остановите туннель и пробросы портов через **Ctrl+C** в соответствующих окнах. Затем остановите кластер:

```powershell
minikube stop -p sentiment-lab
```
