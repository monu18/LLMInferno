# Distributed Conversational Agent System with Bedrock and Ollama
# Author: Monu Kumar
# Email: mkuma47@uic.edu
# UIN: 678818099

## Introduction
The LLMInferno project implements a distributed conversational agent system using Amazon Bedrock and Ollama models. The application is designed to respond to client queries using RESTful APIs and integrates both trained LLM and local models for enhanced conversation capabilities. This project is the final part of a series to develop and deploy LLM-based generative systems using Spark, gRPC, AWS Lambda, and Akka HTTP.

**Video Link:** [https://youtu.be/A4RKetC9_5U] (The video explains the deployment of the conversational application(between Bedrock and Ollama) in the AWS EMR Cluster and the project structure.)

## [Docker Setup & Deployment Guide](READMEDOCKER.md) Click here

## Features
- **Distributed Conversational Agent:** Combines Bedrock LLM and local Ollama models to handle dynamic queries.
- **RESTful API:** Built using Akka HTTP to provide seamless communication between client and server.
- **Scalable Deployment:** Deployed on AWS EC2 and integrated with AWS Lambda.
- **Dynamic Query Handling:** Automatically generates follow-up queries using prompt engineering techniques.

## Environment
- **OS:** Mac
- **IDE:** IntelliJ IDEA 2024.2.1 (Ultimate Edition)
- **Scala Version:** 2.12.20
- **SBT Version:** 1.10.3
- **Akka HTTP Version:** 10.5.3
- **Amazon Bedrock Integration**

## curl for client to access(from anywhere) the Health checkup, Bedrock, Ollama and Conversation brtween Bedrock and ollama

```bash
curl ec2-18-212-89-181.compute-1.amazonaws.com:8080/api/health
```

```bash
curl -X POST ec2-18-212-89-181.compute-1.amazonaws.com:8080/api/generate/bedrock   -H "Content-Type: application/json"   -d '{"prompt": "what is cloud computing", "maxTokens": 100}'
```

```bash
curl -X POST ec2-18-212-89-181.compute-1.amazonaws.com:8080/api/generate/ollama   -H "Content-Type: application/json"   -d '{"prompt": "what is cloud computing", "maxTokens": 100}'
```

```bash
curl -X POST ec2-18-212-89-181.compute-1.amazonaws.com:8080/api/generate/conversation -H 'Content-Type: application/json' -d '{"prompt":"Tell me about cloud computing?","maxTokens":100,"maxExchanges":3,"saveToFile":true}'
```

## Running the Test File
Test files can be found under the directory `src/test`:
```bash
sbt clean compile test
```

## Running the Project
1. **Clone this repository:**
   ```bash
   git clone https://github.com/monu18/LLMInferno
   ```
2. **Navigate to the Project:**
   ```bash
   cd LLMInferno
   ```
3. **Checkout main branch(if not):**
   ```bash
   git checkout origin main
   ```
4. **Clean sbt:**
   ```bash
   sbt clean
   ``` 
5. **Generate protoc:**
   ```bash
   sbt protocGenerate
   ```
6. **Clean compile:**
   ```bash
   sbt clean compile
   ``` 
7. **Run:**
   ```bash
   sbt run
   ``` 
8. **Open the project in IntelliJ:**  
   [How to Open a Project in IntelliJ](https://www.jetbrains.com/help/idea/import-project-or-module-wizard.html#open-project)

[Note: before running the application run ollama(model llama3.2:1b) first and configure Api Gateway to lambda function(having Bedrock implementation]

## Configuration
The application reads configuration values from application.conf. the `application.conf` file found in `src/main/resources/`, allowing for flexibility in path settings.

1. **Configuration Components:**
    1. AWS Configuration:
        1. Lambda API URL configuration
        2. AWS Bedrock client settings
    2. Ollama Configuration:
        1. Host URL and model settings
    3. Application Settings:
        1. Output directory path
        2. Maximum conversation exchanges

## Project Structure
The project comprises the following key components:

1. **API Server and Routes**
    1.	RESTful endpoints for text generation(Bedrock & ollama)
    2.	Conversation management endpoints
    3.	Health check or monitoring endpoint

2. **Client Integration**
    1.	AWS Bedrock client implementation
    2.	Ollama client for local model inference

3. **Conversation Management**
    1.	Conversation tracking and writing in a file
    2.	Exchange limit enforcement

4. **Output Management**
    1.	Conversation file storage


## Prerequisites
Before starting the project, ensure you have the following tools and accounts set up:
- **AWS Account:** Create an AWS account and configure Api Gateway for Lambda invocation along with Bedrock access.
- **Java:** Ensure that Java is installed and properly configured.
- **Git and GitHub:** Use Git for version control and host your project repository on GitHub.
- **Ollama:** Local installation with model(llama3.2:1b) downloaded.
- **IDE:** Choose an Integrated Development Environment (IDE) for coding and development.

## Conclusion
LLMInferno demonstrates the integration of distributed systems, cloud services, and LLMs to create a conversational agent capable of handling real-world scenarios. By leveraging both Bedrock and Ollama, it bridges the gap between cloud-hosted and local AI models for seamless query handling.
