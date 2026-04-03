#!/bin/bash

cleanup() {
    echo -e "\nClosing tunnels..."
    kill $(jobs -p)
    exit
}

trap cleanup SIGINT

echo "Opening tunnels to K3s..."

kubectl port-forward -n database svc/postgres-postgresql 5432:5432 &
kubectl port-forward -n database svc/redis-master 6379:6379 &
kubectl port-forward -n database svc/minio 9000:9000 &
kubectl port-forward -n observability svc/zipkin 9411:9411 &
kubectl port-forward -n database pod/redisinsight-5d496f8648-dnz44 8001:5540 &

echo "Tunnels open. Press Ctrl+C to stop all."

wait