param([switch]$Kind, [switch]$Minikube)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
if ($Kind -and $Minikube) { throw "Use either -Kind or -Minikube, not both." }
function Need($n) { if (-not (Get-Command $n -ErrorAction SilentlyContinue)) { throw "$n not found." } }
Need docker
Need kubectl
Write-Host "==> API image"
docker build -t courier-tracking-api:local .
Write-Host "==> Frontend image"
docker build -t courier-tracking-frontend:local -f frontend/Dockerfile.prod frontend
if ($Kind) {
  Need kind
  if ((kind get clusters 2>$null) -notcontains "courier") { kind create cluster --config k8s/kind-cluster.yaml }
  else { kind export kubeconfig --name courier | Out-Null }
  kind load docker-image courier-tracking-api:local --name courier
  kind load docker-image courier-tracking-frontend:local --name courier
}
if ($Minikube) {
  Need minikube
  $status = minikube status --format '{{.Host}}' 2>$null
  if ($status -ne "Running") {
    minikube start --driver=docker
  }
  $ns = kubectl get ns courier-tracking --ignore-not-found -o name
  if ($ns) {
    kubectl -n courier-tracking scale deploy/courier-api deploy/courier-frontend --replicas=0 --ignore-not-found
    kubectl -n courier-tracking wait --for=delete pod -l app.kubernetes.io/name=courier-api --timeout=90s 2>$null
    kubectl -n courier-tracking wait --for=delete pod -l app.kubernetes.io/name=courier-frontend --timeout=90s 2>$null
  }
  minikube ssh -- "docker rmi -f courier-tracking-api:local courier-tracking-frontend:local || true"
  minikube image load courier-tracking-api:local
  minikube image load courier-tracking-frontend:local
}
if (-not (Test-Path ".env")) {
  throw ".env bulunamadi. .env.example dosyasini .env olarak kopyalayip guvenli degerlerle doldurun."
}
Write-Host "==> Runtime secrets"
kubectl apply -f k8s/base/namespace.yaml
kubectl -n courier-tracking create secret generic courier-secrets --from-env-file=.env --dry-run=client -o yaml |
  kubectl apply -f -
kubectl apply -k k8s/overlays/local
kubectl -n courier-tracking rollout status statefulset/postgres --timeout=180s
kubectl -n courier-tracking rollout status deployment/redis --timeout=120s
kubectl -n courier-tracking rollout status deployment/rabbitmq --timeout=180s
kubectl -n courier-tracking rollout status deployment/courier-api --timeout=240s
kubectl -n courier-tracking rollout status deployment/courier-frontend --timeout=120s
kubectl -n courier-tracking get pods,svc
if ($Minikube) {
  Write-Host "Windows + Docker driver: NodePort is not on localhost. Use port-forward (does not block this script):"
  Write-Host "  kubectl -n courier-tracking port-forward svc/courier-frontend 18080:80"
  Write-Host "  kubectl -n courier-tracking port-forward svc/courier-api 18081:8080"
  Write-Host "Then UI http://localhost:18080  and API http://localhost:18081/swagger-ui.html"
  Write-Host "Optional (keeps a terminal open): minikube service courier-frontend -n courier-tracking"
} else {
  Write-Host "Frontend http://localhost:30080"
  Write-Host "Swagger  http://localhost:30808/swagger-ui.html"
}