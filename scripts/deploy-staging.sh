#!/bin/bash
set -e

SERVER=${1:-"ec2-user@YOUR_EC2_IP"}
KEY=${2:-"~/.ssh/amp-staging-key.pem"}

echo "=== Building backend ==="
cd backend
./mvnw package -DskipTests -B
docker build -t amp-api:latest .
docker save amp-api:latest | gzip > /tmp/amp-api.tar.gz
cd ..

echo "=== Building frontend ==="
cd frontend
VITE_API_BASE_URL=/api/v1 npm run build-only
docker build -t amp-frontend:latest --build-arg VITE_API_BASE_URL=/api/v1 .
docker save amp-frontend:latest | gzip > /tmp/amp-frontend.tar.gz
cd ..

echo "=== Uploading to server ==="
scp -i $KEY /tmp/amp-api.tar.gz $SERVER:/home/ec2-user/amp/
scp -i $KEY /tmp/amp-frontend.tar.gz $SERVER:/home/ec2-user/amp/

echo "=== Deploying on server ==="
ssh -i $KEY $SERVER << 'REMOTE'
cd /home/ec2-user/amp
docker load < amp-api.tar.gz
docker load < amp-frontend.tar.gz
docker-compose down
docker-compose up -d
docker-compose ps
echo "=== Deploy complete ==="
REMOTE

echo "=== Cleaning up ==="
rm -f /tmp/amp-api.tar.gz /tmp/amp-frontend.tar.gz
echo "Done! App should be live at http://YOUR_EC2_IP"
