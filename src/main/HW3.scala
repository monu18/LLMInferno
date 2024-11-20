
import grpc.TextGenerationServiceImpl
import aws.BedrockClient
import ollama.OllamaClient
import io.grpc.ServerBuilder
import textgeneration.textgeneration.TextGenerationServiceGrpc

import scala.concurrent.ExecutionContext

object HW3 extends App {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val bedrockClient = new BedrockClient()
  val ollamaClient = new OllamaClient()
  val service = new TextGenerationServiceImpl(bedrockClient, ollamaClient)

  val server = ServerBuilder
    .forPort(50051)
    .addService(TextGenerationServiceGrpc.bindService(service, ec))
    .build()

  server.start()
  println("gRPC server started on port 50051")
  server.awaitTermination()
}