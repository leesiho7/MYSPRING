#!/bin/bash
set -e

echo "========================================================"
echo "🚀 [Aether Trading Intelligence] Docker Production Deploy"
echo "========================================================"

# 1. Create persistent data volume directories
mkdir -p data/mysql data/logs data/bot_scripts data/mysql_init

# 2. Check and initialize .env
if [ ! -f .env ]; then
  echo "📋 Creating .env from .env.example..."
  cp .env.example .env
  echo "⚠️ Please edit .env with your production credentials if needed."
fi

# 3. Docker Compose Build & Run
echo "📦 Building and starting all containers in detached mode..."
docker compose down --remove-orphans || true
docker compose up -d --build

echo ""
echo "========================================================"
echo "✅ [SUCCESS] Aether Production Containers are Online!"
echo "========================================================"
echo "🌐 Frontend URL:      http://localhost:3000"
echo "🌐 Backend API URL:   http://localhost:8080"
echo "📊 Database Port:     3306 (Persistent Volume in ./data/mysql)"
echo "📝 Log Files:         ./data/logs"
echo "========================================================"