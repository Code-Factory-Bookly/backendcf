# Despliegue Kubernetes y observabilidad

Los manifiestos de esta carpeta despliegan una instancia demostrativa de Bookly con PostgreSQL,
Prometheus y Grafana. Para producción se recomienda sustituir el `StatefulSet` de PostgreSQL por una
base administrada con respaldos y usar un registro privado de imágenes.

## Tecnología recomendada para desarrollo local

Se recomienda [kind](https://kind.sigs.k8s.io/docs/user/quick-start/) (Kubernetes IN Docker) para
probar este despliegue localmente. kind ejecuta los nodos de Kubernetes como contenedores Docker, por
lo que no requiere una máquina virtual adicional y permite cargar directamente la imagen local del
backend en el clúster. Es una buena opción para validar manifiestos, probes, servicios y observabilidad
antes de publicar la imagen en un registro.

Minikube también es válido, pero para este repositorio kind mantiene el flujo más corto y reproducible.

## 1. Requisitos previos

Instalar y dejar ejecutándose:

- Docker Desktop.
- `kubectl`.
- `kind`.

En macOS con Homebrew:

```bash
brew install kubectl kind
docker version
kind version
kubectl version --client
```

Si Docker no está iniciado, kind no podrá crear el clúster.

## 2. Crear el clúster local

Desde la raíz del repositorio:

```bash
kind create cluster --name bookly-local --wait 60s
kubectl cluster-info --context kind-bookly-local
```

El clúster se llama `bookly-local` para evitar confundirlo con otros clústeres locales.

## 3. Construir y cargar la imagen del backend

La imagen debe conservar la etiqueta `0.0.2`, porque el manifiesto usa esa etiqueta y
`imagePullPolicy: IfNotPresent`:

```bash
docker build -t bookly/backendcf:0.0.2 .
kind load docker-image bookly/backendcf:0.0.2 --name bookly-local
```

Este flujo evita publicar la imagen en Docker Hub. En un entorno compartido o productivo, sustituir
`bookly/backendcf:0.0.2` en `backend.yaml` por la imagen del registro autorizado.

## 4. Crear los secretos del entorno local

Aplicar primero el namespace:

```bash
kubectl apply -f k8s/namespace.yaml
```

Crear o actualizar el secreto sin guardar los valores en Git:

```bash
kubectl -n bookly create secret generic bookly-secrets \
  --from-literal=POSTGRES_USER=backendcf \
  --from-literal=POSTGRES_PASSWORD='cambiar-por-un-secreto' \
  --from-literal=JWT_SECRET='cambiar-por-un-secreto-largo' \
  --from-literal=MFA_ENCRYPTION_KEY='cambiar-por-una-clave-de-32-bytes' \
  --from-literal=GRAFANA_ADMIN_USER=admin \
  --from-literal=GRAFANA_ADMIN_PASSWORD='cambiar-por-un-secreto' \
  --dry-run=client -o yaml | kubectl apply -f -
```

Para un entorno real, usar valores generados y un gestor de secretos. No aplicar
`secret.example.yaml` con sus valores de ejemplo.

## 5. Desplegar Bookly, Prometheus y Grafana

```bash
kubectl apply -k k8s/
kubectl -n bookly get pods
kubectl -n bookly rollout status statefulset/bookly-postgres --timeout=180s
kubectl -n bookly rollout status deployment/bookly-api --timeout=180s
kubectl -n bookly rollout status deployment/bookly-prometheus --timeout=120s
kubectl -n bookly rollout status deployment/bookly-grafana --timeout=120s
```

Kustomize genera internamente los ConfigMaps de Prometheus y Grafana a partir de los archivos de
`k8s/config/`. No es necesario crear esos ConfigMaps manualmente.

## 6. Ejecutar y verificar la aplicación

Abrir terminales separadas para los port-forward:

```bash
kubectl -n bookly port-forward svc/bookly-api 8080:8080
kubectl -n bookly port-forward svc/bookly-api 8081:8081
kubectl -n bookly port-forward svc/bookly-grafana 3000:3000
kubectl -n bookly port-forward svc/bookly-prometheus 9090:9090
```

Validar la API y el puerto de gestión:

```bash
curl -s http://localhost:8081/actuator/health/liveness
curl -s http://localhost:8081/actuator/health/readiness
curl -s http://localhost:8081/actuator/prometheus | head
```

La API pública queda disponible en `http://localhost:8080`; el puerto de gestión y Actuator están en
`http://localhost:8081`. Grafana queda en
`http://localhost:3000` y usa el usuario y la contraseña definidos en `bookly-secrets`. Prometheus
queda en `http://localhost:9090`. En el entorno local, `/actuator/health` puede devolver `503` si no
se configuran credenciales SMTP; las probes de Kubernetes usan `liveness` y `readiness` para no hacer
depender la disponibilidad del backend de ese servicio externo opcional.

## 7. Verificar observabilidad

En Prometheus, abrir `http://localhost:9090/targets` y comprobar que el target `bookly-api` esté en
estado `UP`. La URL de scrape es `http://bookly-api:8081/actuator/prometheus` dentro del clúster.

En Grafana:

1. Iniciar sesión con las credenciales de `bookly-secrets`.
2. Abrir el dashboard `Bookly - Observabilidad`.
3. Confirmar los paneles de solicitudes, latencia p95, memoria JVM y uptime del proceso.

Si el target no está disponible, revisar primero:

```bash
kubectl -n bookly get pods,svc
kubectl -n bookly logs deployment/bookly-api
kubectl -n bookly logs deployment/bookly-prometheus
kubectl -n bookly describe pod -l app.kubernetes.io/name=bookly-api
```

## 8. Actualizar la imagen después de cambios

Usar una etiqueta nueva para evitar confundir una imagen antigua:

```bash
docker build -t bookly/backendcf:0.0.3 .
kind load docker-image bookly/backendcf:0.0.3 --name bookly-local
```

Después actualizar la etiqueta `image` en `k8s/backend.yaml` y aplicar de nuevo:

```bash
kubectl apply -k k8s/
kubectl -n bookly rollout status deployment/bookly-api --timeout=180s
```

## 9. Detener y limpiar el entorno local

Para conservar el clúster y detener solo los port-forward, cerrar las terminales que ejecutan
`kubectl port-forward`.

Para eliminar completamente el clúster local y sus datos de demostración:

```bash
kind delete cluster --name bookly-local
```

Este comando elimina el clúster, PostgreSQL, Prometheus y Grafana de kind. No elimina las imágenes
locales de Docker ni ningún archivo del repositorio.
