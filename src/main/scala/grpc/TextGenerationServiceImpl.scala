package grpc

import textgeneration.text_generation._
import textgeneration.text_generation.TextGenerationServiceGrpc.TextGenerationService
import aws.BedrockClient
import ollama.OllamaClient

import scala.concurrent.{ExecutionContext, Future}

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