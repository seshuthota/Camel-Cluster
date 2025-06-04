#!/bin/bash

# Apache Camel Cluster Build Script
# Builds all applications and Docker images

set -e  # Exit on any error

echo "🚀 Starting Apache Camel Cluster Build..."
echo "================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    print_error "Docker is not running. Please start Docker and try again."
    exit 1
fi

# Clean previous builds
print_status "Cleaning previous builds..."
docker-compose down --remove-orphans 2>/dev/null || true
docker system prune -f --volumes 2>/dev/null || true

# Build parent POM first
print_status "Building parent POM..."
cd camel-cluster-parent
mvn clean install -DskipTests
cd ..

# Build common module
print_status "Building common module..."
cd camel-cluster-common
mvn clean install -DskipTests
cd ..

# Build with Maven
echo "📦 Building Maven modules..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Maven build failed!"
    exit 1
fi

echo "✅ Maven build completed successfully!"

# Build Docker images
echo "🐳 Building Docker images..."

echo "Building Producer image..."
docker build -f camel-producer/Dockerfile -t camel-cluster/producer:latest .

echo "Building Consumer image..."
docker build -f camel-consumer/Dockerfile -t camel-cluster/consumer:latest .

echo "Building Coordinator image..."
docker build -f camel-coordinator/Dockerfile -t camel-cluster/coordinator:latest .

# Check if all images were built successfully
if [ $? -eq 0 ]; then
    echo "✅ All Docker images built successfully!"
    echo ""
    echo "📊 Build Summary:"
    echo "=================="
    echo "🏭 Producer image: camel-cluster/producer:latest"
    echo "📥 Consumer image: camel-cluster/consumer:latest"
    echo "🎯 Coordinator image: camel-cluster/coordinator:latest"
    echo ""
    echo "🏗️ Updated Cluster Architecture:"
    echo "=================================="
    echo "📊 Producer instances: 1"
    echo "📥 Consumer instances: 2"
    echo "🎯 Coordinator instances: 2 (HIGH AVAILABILITY)"
    echo "🗄️ Database: PostgreSQL"
    echo "📨 Message Broker: ActiveMQ"
    echo "⚖️ Load Balancer: Nginx"
    echo ""
    echo "🎉 BUILD COMPLETED SUCCESSFULLY!"
    echo ""
    echo "🚀 Next steps:"
    echo "1. Deploy: docker-compose up -d"
    echo "2. Scale consumers: docker-compose up -d --scale consumer1=3"
    echo "3. Monitor: http://localhost:8161 (ActiveMQ Console)"
    echo "4. Test endpoints:"
    echo "   - Producer: http://localhost:8081/api/producer/status"
    echo "   - Consumer: http://localhost/api/consumer/status (via nginx)"
    echo "   - Coordinator 1: http://localhost:8083/api/coordinator/status"
    echo "   - Coordinator 2: http://localhost:8086/api/coordinator/status"
else
    echo "❌ Docker image build failed!"
    exit 1
fi 