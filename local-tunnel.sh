#!/bin/bash

# Function to kill all background jobs when you press Ctrl+C
cleanup() {
    echo -e "\nClosing tunnels..."
    kill $(jobs -p)
    exit
}

trap cleanup SIGINT

echo "Opening tunnels to K3s..."

# 1. Postgres
kubectl port-forward -n database svc/postgres-postgresql 5432:5432 &

# 2. redis
kubectl port-forward -n database svc/redis-master 6379:6379 &

# 2. Feedzmore App
# kubectl port-forward -n app deployment/feedzmore-app 9091:9091 &

# 3. Minio
kubectl port-forward -n database svc/minio 9000:9000 &

# 4. Zipkin (Added)
# Mapping the remote 9411 to your local 9411
kubectl port-forward -n observability svc/zipkin 9411:9411 &

echo "Tunnels open. Press Ctrl+C to stop all."

# Wait for all background processes to finish (which keeps the script running)
wait