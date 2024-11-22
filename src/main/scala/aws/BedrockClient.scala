package aws

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

// JSON Protocol
object JsonProtocol extends DefaultJsonProtocol

class BedrockClient(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(getClass)

  private val config = ConfigFactory.load()
  private val apiUrl = config.getString("aws.lambda.apiUrl") // API Gateway URL

  def generateSentence(prompt: String, maxTokens: Int): Future[JsValue] = {
    logger.info(s"Preparing to generate sentence for prompt: '$prompt' with maxTokens: $maxTokens")

    // Escape the prompt to handle special characters
    val escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    logger.debug(s"Escaped prompt: $escapedPrompt")

    // Prepare payload for API Gateway
    val payload = s"""{
      "prompt": "Respond to: $escapedPrompt",
      "max_tokens": $maxTokens,
      "temperature": 0.7
    }"""
    logger.debug(s"Payload for API Gateway: $payload")

    // Create HTTP request
    val httpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$apiUrl/invoke",
      entity = HttpEntity(ContentTypes.`application/json`, payload)
    )

    // Send request and handle response
    Http().singleRequest(httpRequest).flatMap { response =>
      logger.info(s"Received response with status: ${response.status}")

      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[String].map { jsonString =>
            logger.debug(s"Raw response from API Gateway: $jsonString")
            jsonString.parseJson // Return raw JSON as `JsValue`
          }
        case _ =>
          Unmarshal(response.entity).to[String].flatMap { errorBody =>
            logger.error(s"Request failed with status ${response.status}: $errorBody")
            Future.failed(new RuntimeException(s"Request failed with status ${response.status}: $errorBody"))
          }
      }
    }.recoverWith {
      case ex: Exception =>
        logger.error(s"Error while invoking Bedrock API: ${ex.getMessage}", ex)
        Future.failed(ex)
    }
  }
}