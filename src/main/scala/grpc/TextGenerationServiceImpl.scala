package grpc

import textgeneration.text_generation._
import textgeneration.text_generation.TextGenerationServiceGrpc.TextGenerationService
import aws.BedrockClient
import ollama.OllamaClient
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

class TextGenerationServiceImpl(
                                 bedrockClient: BedrockClient,
                                 ollamaClient: OllamaClient
                               )(implicit ec: ExecutionContext)
  extends TextGenerationService {

  override def generateSentence(request: GenerateRequest): Future[GenerateResponse] = {
    bedrockClient.generateSentence(request.query, request.maxLength).map { json =>
      // Extract the "sentences" field from the JSON response
      val sentences = json.asJsObject.getFields("sentences") match {
        case Seq(JsArray(elements)) =>
          elements.collect {
            case JsString(sentence) => sentence
          }
        case _ => Seq("Unexpected JSON structure!")
      }
      GenerateResponse(sentences)
    }.recover { case ex =>
      // Handle errors gracefully
      GenerateResponse(Seq(s"Error generating sentence: ${ex.getMessage}"))
    }
  }
}