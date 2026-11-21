# Перенос Spring Boot в Docker и Minikube

Команды предназначены для Windows PowerShell. Выполняйте их из корня проекта, где находятся `pom.xml` и `Dockerfile`. Операции с кластером используют профиль `sentiment-lab`, namespace `sentiment` и имя Deployment `sentiment`. Если ранее в этом профиле уже было приложение из общего учебного проекта, эти манифесты обновят его образ до `sentiment-spring:1.0.0`.

## 1. Сборка и локальная проверка контейнера

Откройте Docker Desktop через меню Пуск и дождитесь запуска движка.

```powershell
docker version
docker info --format '{{.OSType}}'
docker build -t sentiment-spring:1.0.0 .
if ($LASTEXITCODE -ne 0) { throw 'Docker build failed' }

$size = docker image inspect sentiment-spring:1.0.0 --format '{{.Size}}'
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect image' }
$bytes = [int64]$size
'{0:N0} bytes = {1:N2} MB' -f $bytes, ($bytes / 1000000)
if ($bytes -ge 150000000) { throw 'Image must be smaller than 150 MB' }

docker run --rm -d --name sentiment-spring-check --memory=384m -p 127.0.0.1:18000:8080 -p 127.0.0.1:18001:8081 sentiment-spring:1.0.0
docker logs sentiment-spring-check
curl.exe -fsS http://127.0.0.1:18000/readyz
```

После готовности проверьте API и метрики. Если `/readyz` ещё недоступен, повторите проверку через несколько секунд.

```powershell
curl.exe -fsSi "http://127.0.0.1:18000/api/sentiment?text=hello"
curl.exe -fsS http://127.0.0.1:18001/actuator/prometheus | Select-String 'http_server_requests_seconds_count'
docker stop sentiment-spring-check
```

Ожидаются HTTP 200 и `{"sentiment":"positive"}`. Сохраните скриншоты размера образа и ответа API.

## 2. Кластер и перенос образа

Запустите профиль с двумя узлами. Конфигурация ниже соответствует исходному плану задания. Если доступных Docker ресурсов мало, используйте `--cpus=2 --memory=4096` и зафиксируйте параметры в отчёте.

```powershell
minikube start -p sentiment-lab --driver=docker --container-runtime=containerd --cpus=4 --memory=8192 --nodes=2
if ($LASTEXITCODE -ne 0) { throw 'Minikube start failed' }

function kubectl {
    minikube -p sentiment-lab kubectl -- --context sentiment-lab @args
}

kubectl get nodes -o wide
minikube addons enable metrics-server -p sentiment-lab
kubectl -n kube-system rollout status deployment/metrics-server --timeout=240s
kubectl wait --for=condition=Available apiservice/v1beta1.metrics.k8s.io --timeout=240s

docker save -o sentiment-spring-image.tar sentiment-spring:1.0.0
if ($LASTEXITCODE -ne 0) { throw 'Image export failed' }
minikube image load sentiment-spring-image.tar -p sentiment-lab
if ($LASTEXITCODE -ne 0) { throw 'Image import failed' }
```

Проверьте образ на каждом узле. В Deployment задано `imagePullPolicy: Never`, поэтому образ должен быть загружен до создания Pod.

```powershell
$nodes = kubectl get nodes -o name
foreach ($node in $nodes) {
    $name = $node -replace '^node/', ''
    Write-Output $name
    minikube ssh -p sentiment-lab --node $name -- "sudo crictl images" | Select-String 'sentiment-spring'
}
```

[Документация загрузки образов Minikube](https://minikube.sigs.k8s.io/docs/commands/image/)

## 3. Ingress, три реплики и HPA

Установите Traefik. При повторном выполнении Helm обновит одноимённый релиз.

```powershell
helm repo add traefik https://traefik.github.io/charts
helm repo update
$traefikChart = (helm search repo traefik/traefik -o json | ConvertFrom-Json | Where-Object { $_.name -eq 'traefik/traefik' }).version
$traefikChart
helm upgrade --install traefik traefik/traefik --version $traefikChart --kube-context sentiment-lab --namespace ingress --create-namespace --values helm/traefik-values.yaml --wait --timeout 8m
if ($LASTEXITCODE -ne 0) { throw 'Traefik installation failed' }

kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/app.yaml
kubectl -n sentiment rollout status deployment/sentiment --timeout=240s
kubectl -n sentiment get deployment,service,ingress,hpa,pods -o wide
kubectl -n sentiment top pods
```

В манифестах заданы три начальные реплики, LoadBalancer, Ingress `/api`, внутренний Service для Actuator и HPA с диапазоном 3–6. Целевой CPU — 60% от `requests.cpu: 100m`. Подождите появления числовой метрики CPU перед нагрузочным прогоном.

[Traefik в Kubernetes](https://doc.traefik.io/traefik/setup/kubernetes/) · [HPA](https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/)

## 4. Доступ к приложению

В отдельной вкладке запустите туннель и оставьте вкладку открытой.

```powershell
minikube tunnel -p sentiment-lab
```

В основной вкладке проверьте LoadBalancer.

```powershell
kubectl -n sentiment get service sentiment
$lb = kubectl -n sentiment get service sentiment -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
if (-not $lb) { throw 'Wait for an EXTERNAL-IP' }
curl.exe -fsSi "http://${lb}:8080/api/sentiment?text=hello"
```

В ещё одной вкладке откройте порт Traefik. Запрос через этот порт проверяет Ingress.

```powershell
minikube -p sentiment-lab kubectl -- --context sentiment-lab -n ingress port-forward svc/traefik 18080:80
```

В основной вкладке выполните запросы и посчитайте ответы каждого Pod по заголовку.

```powershell
curl.exe -fsSi "http://127.0.0.1:18080/api/sentiment?text=hello"
$pods = foreach ($request in 1..18) {
    curl.exe -fsS -D - -o NUL "http://127.0.0.1:18080/api/sentiment?text=hello" |
        Select-String '^X-Pod-Name:' |
        ForEach-Object { ($_.Line -split ':', 2)[1].Trim() }
}
$pods | Group-Object -NoElement | Sort-Object Name
```

## 5. Prometheus и Grafana

```powershell
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
$promChart = (helm search repo prometheus-community/kube-prometheus-stack -o json | ConvertFrom-Json | Where-Object { $_.name -eq 'prometheus-community/kube-prometheus-stack' }).version
$promChart
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack --version $promChart --kube-context sentiment-lab --namespace monitoring --create-namespace --values helm/monitoring-values.yaml --wait --timeout 12m
if ($LASTEXITCODE -ne 0) { throw 'Monitoring installation failed' }

kubectl wait --for=condition=Established crd/servicemonitors.monitoring.coreos.com --timeout=120s
kubectl apply -f k8s/service-monitor.yaml
kubectl -n monitoring create configmap sentiment-dashboard --from-file=sentiment.json=grafana/sentiment.json --dry-run=client -o yaml | kubectl apply -f -
kubectl -n monitoring label configmap sentiment-dashboard grafana_dashboard=1 --overwrite
kubectl -n monitoring get pods,svc
```

В двух отдельных вкладках запустите по одному пробросу портов.

```powershell
minikube -p sentiment-lab kubectl -- --context sentiment-lab -n monitoring port-forward svc/lab-monitoring-prometheus 19090:9090
```

```powershell
minikube -p sentiment-lab kubectl -- --context sentiment-lab -n monitoring port-forward svc/lab-grafana 13000:80
```

В браузере откройте `http://127.0.0.1:19090/targets` и найдите `sentiment-metrics`. После успешного опроса ожидается UP для готовых Pod. Получите пароль Grafana в буфер обмена.

```powershell
$password = kubectl -n monitoring get secret lab-grafana -o jsonpath='{.data.admin-password}'
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($password)) | Set-Clipboard
```

Откройте `http://127.0.0.1:13000/d/sentiment-lab`, войдите как `admin` и вставьте пароль. Дашборд показывает HTTP RPS, p95, CPU по Pod и число реплик HPA. Для появления данных отправьте запросы или запустите нагрузку.

## 6. Демонстрация HPA

В отдельной вкладке наблюдайте HPA.

```powershell
minikube -p sentiment-lab kubectl -- --context sentiment-lab -n sentiment get hpa sentiment -w
```

В основной вкладке создайте ConfigMap и нагрузочный Job на пять минут.

```powershell
kubectl -n sentiment create configmap sentiment-load-script --from-file=load.js=load/load.js --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f load/job.yaml
kubectl -n sentiment get hpa,pods
kubectl -n sentiment top pods
kubectl -n sentiment describe hpa sentiment
```

После теста прочитайте итоговый журнал и дождитесь возврата к трём репликам.

```powershell
kubectl -n sentiment wait --for=condition=complete job/sentiment-load --timeout=420s
kubectl -n sentiment logs job/sentiment-load
```

Для повторного прогона удалите завершённый Job, затем примените его снова. ConfigMap можно обновлять повторным выполнением команды его создания с `--dry-run=client` и `apply`. Если CPU ниже цели, увеличьте `VUS` в `load/job.yaml` и отметьте это изменение в результатах.

```powershell
kubectl -n sentiment delete job sentiment-load --ignore-not-found
kubectl apply -f load/job.yaml
```

## 7. Обновление версии и откат

После изменения кода соберите новый тег и перенесите его на узлы. Старая версия образа остаётся доступной для отката.

```powershell
docker build -t sentiment-spring:1.0.1 .
if ($LASTEXITCODE -ne 0) { throw 'Docker build failed' }
docker save -o sentiment-spring-image-v1.0.1.tar sentiment-spring:1.0.1
if ($LASTEXITCODE -ne 0) { throw 'Image export failed' }
minikube image load sentiment-spring-image-v1.0.1.tar -p sentiment-lab
if ($LASTEXITCODE -ne 0) { throw 'Image import failed' }

kubectl -n sentiment set image deployment/sentiment api=docker.io/library/sentiment-spring:1.0.1
kubectl -n sentiment rollout status deployment/sentiment --timeout=240s
kubectl -n sentiment get pods -o wide
kubectl -n sentiment rollout history deployment/sentiment
```

После успешного обновления укажите тот же тег `1.0.1` в `k8s/app.yaml`, чтобы файл соответствовал работающему Deployment. Для возврата к предыдущей ревизии используйте:

```powershell
kubectl -n sentiment rollout undo deployment/sentiment
kubectl -n sentiment rollout status deployment/sentiment --timeout=240s
kubectl -n sentiment get deployment sentiment -o jsonpath='{.spec.template.spec.containers[0].image}'
```

После отката также приведите тег в `k8s/app.yaml` к фактическому. Для смены версии применяется `set image`, поэтому HPA продолжает управлять текущим количеством реплик.

## Проверки и завершение

Сохраните в отчёт точный размер образа, ответы API, три готовые реплики, HTTP через Ingress, статусы Prometheus UP и график масштабирования. При проблеме изучите события и журналы конкретного Pod.

```powershell
kubectl -n sentiment get events --sort-by=.metadata.creationTimestamp
kubectl -n sentiment get pods
kubectl -n sentiment describe deployment sentiment
```

Остановите туннель и пробросы портов через Ctrl+C, затем при необходимости остановите кластер.

```powershell
minikube stop -p sentiment-lab
```
