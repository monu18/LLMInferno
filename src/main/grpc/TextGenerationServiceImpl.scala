package grpc

import textgeneration.textgeneration._
import textgeneration.textgeneration.TextGenerationServiceGrpc.TextGenerationService
import aws.BedrockClient
import ollama.OllamaClient
import scala.concurrent.{Future, ExecutionContext}

class TextGenerationServiceImpl(
                                 bedrockClient: BedrockClient,
                                 ollamaClient: OllamaClient
                               )(implicit ec: ExecutionContext) extends TextGenerationService {

  override def generateSentence(request: GenerateRequest): Future[GenerateResponse] = {
    val sentences = bedrockClient.generateSentence(request.query, request.maxLength)
    Future.successful(GenerateResponse(sentences))
  }

  override def generateNextWord(request: WordRequest): Future[WordResponse] = {
    val nextWord = bedrockClient.generateNextWord(request.context)
    Future.successful(WordResponse(nextWord))
  }
}