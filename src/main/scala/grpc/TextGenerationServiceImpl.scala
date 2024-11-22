package grpc

import textgeneration.text_generation._
import textgeneration.text_generation.TextGenerationServiceGrpc.TextGenerationService
import aws.BedrockClient
import ollama.OllamaClient
import spray.json._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class TextGenerationServiceImpl(
                                 bedrockClient: BedrockClient,
                                 ollamaClient: OllamaClient
                               )(implicit ec: ExecutionContext)
  extends TextGenerationService {

  private val logger = LoggerFactory.getLogger(getClass)

  override def generateSentence(request: GenerateRequest): Future[GenerateResponse] = {
    logger.info(s"Received GenerateRequest with query: '${request.query}' and maxLength: ${request.maxLength}")

    bedrockClient.generateSentence(request.query, request.maxLength).map { json =>
      logger.debug(s"Raw JSON response from Bedrock: ${json.prettyPrint}")

      // Extract the "response" field from the JSON response
      val sentences = json.asJsObject.getFields("body") match {
        case Seq(JsString(body)) =>
          body.parseJson.asJsObject.getFields("response") match {
            case Seq(JsString(sentence)) =>
              logger.info(s"Generated sentence: $sentence")
              Seq(sentence)
            case _ =>
              logger.error("Unexpected structure in 'response' field.")
              Seq("Unexpected response structure from Bedrock.")
          }
        case _ =>
          logger.error("Unexpected structure in 'body' field.")
          Seq("Unexpected JSON structure from Bedrock.")
      }
      GenerateResponse(sentences)
    }.recover { case ex =>
      logger.error(s"Error during Bedrock generation: ${ex.getMessage}", ex)
      GenerateResponse(Seq(s"Error generating sentence: ${ex.getMessage}"))
    }
  }
}