# Запуск приложения в Minikube

Для запуска нужны Docker Desktop, Minikube, kubectl и Helm. Откройте PowerShell в папке проекта.

Сначала соберите образ `sentiment-spring:1.0.0` по [README.md](README.md). Если контейнер Docker ещё работает, остановите его через Ctrl+C.

## 1. Запустить кластер

Откройте Docker Desktop и выполните команды в PowerShell:

```powershell
minikube start -p sentiment-lab --driver=docker --cpus=4 --memory=4096
kubectl config use-context sentiment-lab
kubectl get nodes
```

В списке должен быть один узел со статусом `Ready`. Это значит, что локальный кластер готов к работе.

Включите метрики CPU и загрузите образ из Docker в Minikube:

```powershell
minikube addons enable metrics-server -p sentiment-lab
minikube image load sentiment-spring:1.0.0 -p sentiment-lab
```

## 2. Установить Traefik и запустить приложение

Установите Traefik для работы Ingress. Если Traefik уже есть, Helm его обновит:

```powershell
helm repo add traefik https://traefik.github.io/charts
helm repo update
helm upgrade --install traefik traefik/traefik -n ingress --create-namespace -f helm/traefik-values.yaml
```

Запустите приложение:

```powershell
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/app.yaml
kubectl get pods -n sentiment
```

Дождитесь трёх Pod с `1/1` и `Running`. После этого приложение готово принимать запросы. Ресурсы контейнера, проверки состояния и HPA от трёх до шести реплик уже заданы в файлах проекта.

## 3. Проверить доступ к API

Откройте отдельное окно PowerShell и запустите туннель. Это окно должно оставаться открытым:

```powershell
minikube tunnel -p sentiment-lab
```

Вернитесь в основное окно и посмотрите адрес приложения:

```powershell
kubectl get service sentiment -n sentiment
```

Откройте в браузере адрес из `EXTERNAL-IP` с портом `8080`. Для адреса `127.0.0.1` ссылка будет такой: [http://127.0.0.1:8080/api/sentiment?text=hello](http://127.0.0.1:8080/api/sentiment?text=hello). В ответ должно прийти `{"sentiment":"positive"}`.

Теперь проверьте Ingress. В ещё одном окне PowerShell выполните:

```powershell
kubectl port-forward service/traefik 18080:80 -n ingress
```

Это окно тоже оставьте открытым. В основном окне отправьте запрос:

```powershell
curl.exe -i "http://localhost:18080/api/sentiment?text=hello"
```

В ответе должны быть HTTP 200 и `positive`. Повторите команду несколько раз и посмотрите на `X-Pod-Name`. Эта строка показывает имя Pod, который обработал запрос. Если имена меняются, запросы обрабатывают разные реплики.

## 4. Открыть Prometheus и Grafana

Установите Prometheus и Grafana через Helm. Пароль для входа в Grafana будет `sentiment-demo`:

```powershell
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack -n monitoring --create-namespace -f helm/monitoring-values.yaml --set grafana.adminPassword=sentiment-demo --wait --timeout 10m
kubectl apply -f k8s/service-monitor.yaml
kubectl get pods -n monitoring
```

Дождитесь запуска компонентов мониторинга. Затем откройте ещё два окна PowerShell. В каждом запустите одну из команд ниже и оставьте оба окна открытыми.

Для Prometheus:

```powershell
kubectl port-forward service/lab-monitoring-prometheus 19090:9090 -n monitoring
```

Для Grafana:

```powershell
kubectl port-forward service/lab-grafana 13000:80 -n monitoring
```

Откройте [Prometheus Targets](http://localhost:19090/targets) и найдите приложение `sentiment`. Статус `UP` означает, что Prometheus получает его метрики.

Войдите в [Grafana](http://localhost:13000) с логином `admin` и паролем `sentiment-demo`.

Добавьте дашборд из файла:

1. Откройте **Dashboards**, затем **New → Import dashboard**.
2. Загрузите файл `grafana/sentiment.json` из проекта.
3. Если появится выбор источника данных, выберите Prometheus.
4. Нажмите **Import**.

Графики показывают запросы в секунду, задержку p95, CPU по Pod и число реплик HPA. Чтобы появились данные, отправьте несколько запросов к API или запустите нагрузочный тест. [Документация импорта дашборда](https://grafana.com/docs/grafana/latest/visualizations/dashboards/build-dashboards/import-dashboards/).

## 5. Запустить нагрузочный тест

Создайте ConfigMap со скриптом нагрузки и запустите Job с k6:

```powershell
kubectl create configmap sentiment-load-script --from-file=load/load.js -n sentiment
kubectl apply -f load/job.yaml
```

Тест работает пять минут с 60 виртуальными пользователями. В другом окне посмотрите, как меняется число реплик HPA:

```powershell
kubectl get hpa -n sentiment -w
```

Нажмите Ctrl+C, чтобы вернуться к вводу команд. Сам тест продолжит работать.

Во время теста посмотрите число Pod и загрузку CPU:

```powershell
kubectl get pods -n sentiment
kubectl top pods -n sentiment
```

Когда тест закончится, откройте его лог:

```powershell
kubectl logs job/sentiment-load -n sentiment
```

Если CPU будет выше целевых 60%, HPA может увеличить число реплик до шести. После теста число реплик постепенно вернётся к трём.

Чтобы повторить тест, удалите завершённый Job и запустите его снова. ConfigMap со скриптом остаётся в кластере:

```powershell
kubectl delete job sentiment-load -n sentiment
kubectl apply -f load/job.yaml
```

## 6. Завершить работу

Нажмите Ctrl+C в окнах с туннелем и пробросами портов. Затем остановите кластер:

```powershell
minikube stop -p sentiment-lab
```
