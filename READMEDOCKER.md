# Complete Setup & Deployment Guide - Conversational Agent

## 1. Prerequisites Setup

### 1.1 Local Machine Setup
1. **Install Docker Desktop**
    - Download from [Docker Desktop](https://www.docker.com/products/docker-desktop)
    - Verify installation:
   ```bash
   docker --version
   ```

2. **Install AWS CLI**
    - Download AWS CLI
    - Verify installation:
   ```bash
   aws --version
   ```

3. **Configure AWS Credentials**
   ```bash
   aws configure
   # Enter your credentials:
   AWS Access Key ID: <AWS Access Key ID>
   AWS Secret Access Key: <AWS Secret Access Key>
   Default region: us-east-1
   ```

### 1.2 AWS Setup
1. **Create ECR Repository**
    - Go to AWS Console → ECR
    - Create repository named "conversational-agent"

2. **Add Required IAM Policies**
    - Add to your IAM user:
        - AmazonEC2ContainerRegistryFullAccess
        - AmazonECS_FullAccess

## 2. Project Setup

### 2.1 Required Files
Create these files in your project root:

1. **Dockerfile.server**
```dockerfile
# Build stage
FROM --platform=linux/amd64 eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /build

# Install sbt with cleanup
RUN apt-get update && \
    apt-get install -y curl && \
    curl -L -o sbt.deb https://repo.scala-sbt.org/scalasbt/debian/sbt-1.9.4.deb && \
    dpkg -i sbt.deb && \
    apt-get update && \
    apt-get install -y sbt && \
    rm -rf /var/lib/apt/lists/* && \
    rm sbt.deb

# Copy only necessary files
COPY build.sbt .
COPY project project/
COPY src src/

# Build the application
RUN sbt clean compile assembly

# Runtime stage
FROM --platform=linux/amd64 eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy only the necessary files from builder
COPY --from=builder /build/target/scala-2.12/LLMInferno.jar /app/app.jar
COPY src/main/resources/application.conf /app/application.conf

# Create output directory
RUN mkdir -p /app/src/main/resources/output

ENV JAVA_OPTS="-Dconfig.file=/app/application.conf"

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
```

2. **docker-compose.yml** (for local testing)
```yaml
version: '3.8'
services:
  api-server:
    build:
      context: .
      dockerfile: Dockerfile.server
    ports:
      - "8080:8080"
    environment:
      - AWS_ACCESS_KEY_ID=<ACCESS KEY>
      - AWS_SECRET_ACCESS_KEY=<SECRET KEY>
      - AWS_REGION=us-east-1
    volumes:
      - ./src/main/resources/output:/app/output
    depends_on:
      - ollama
    networks:
      - app-network

  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama
    networks:
      - app-network

networks:
  app-network:
    driver: bridge

volumes:
  ollama_data:
```

## 3. Deployment Steps

### 3.1 Build and Push to ECR
```bash
# 1. Create and use buildx builder
docker buildx create --use

# 2. Build image for AMD64 architecture
docker buildx build --platform linux/amd64 -t conversational-agent -f Dockerfile.server . --load

# 3. Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 867344461242.dkr.ecr.us-east-1.amazonaws.com

# 4. Tag image
docker tag conversational-agent:latest 867344461242.dkr.ecr.us-east-1.amazonaws.com/conversational-agent:latest

# 5. Push to ECR
docker push 867344461242.dkr.ecr.us-east-1.amazonaws.com/conversational-agent:latest
```

### 3.2 EC2 Deployment
1. **Connect to EC2**
   ```bash
   ssh -i your-key.pem ec2-user@ec2-54-235-16-132.compute-1.amazonaws.com
   ```

2. **Install Docker on EC2**
   ```bash
   sudo yum update -y
   sudo yum install -y docker
   sudo service docker start
   sudo usermod -a -G docker ec2-user
   ```

3. **Pull and Run Container**
   ```bash
   aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 867344461242.dkr.ecr.us-east-1.amazonaws.com
   
   docker pull 867344461242.dkr.ecr.us-east-1.amazonaws.com/conversational-agent:latest
   
   docker run -d -p 8080:8080 867344461242.dkr.ecr.us-east-1.amazonaws.com/conversational-agent:latest
   ```

## 4. Testing the Application

### 4.1 Basic Health Check
```bash
curl http://ec2-54-235-16-132.compute-1.amazonaws.com:8080/api/health
```

### 4.2 Test API Endpoints
1. **Health Checkup**
```bash
curl http://ec2-54-235-16-132.compute-1.amazonaws.com:8080/api/health
```

2. **Bedrock Generation**
```bash
curl -X POST http://ec2-54-235-16-132.compute-1.amazonaws.com:8080/api/generate/bedrock \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Tell me what is cloud computing", "maxTokens": 100}'
```

3. **Ollama Generation**
```bash
curl -X POST http://ec2-54-235-16-132.compute-1.amazonaws.com:8080/api/generate/ollama \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Tell me what is cloud computing", "maxTokens": 100}'
```


4. **Conversation Chain**
```bash
curl -X POST http://ec2-54-235-16-132.compute-1.amazonaws.com:8080/api/generate/conversation \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Tell me what is cloud computing",
    "maxTokens": 100,
    "maxExchanges": 2,
    "saveToFile": true
  }'
```

## 5. Troubleshooting
- Check Docker logs: `docker logs [container-id]`
- Verify container running: `docker ps`
- Check EC2 security group allows port 8080
