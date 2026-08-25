param([switch]$Kind)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
function Need($n) { if (-not (Get-Command $n -ErrorAction SilentlyContinue)) { throw "$n yok. Docker Desktop Kubernetes ac." } }
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
kubectl apply -k k8s/overlays/local
kubectl -n courier-tracking rollout status statefulset/postgres --timeout=180s
kubectl -n courier-tracking rollout status deployment/redis --timeout=120s
kubectl -n courier-tracking rollout status deployment/rabbitmq --timeout=180s
kubectl -n courier-tracking rollout status deployment/courier-api --timeout=240s
kubectl -n courier-tracking rollout status deployment/courier-frontend --timeout=120s
kubectl -n courier-tracking get pods,svc
Write-Host "Frontend http://localhost:30080"
Write-Host "Swagger  http://localhost:30808/swagger-ui.html"