package aws

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// JSON Protocol
object JsonProtocol extends DefaultJsonProtocol

class BedrockClient(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext) {
  import JsonProtocol._

  private val config = ConfigFactory.load()
  private val apiUrl = config.getString("aws.lambda.apiUrl") // API Gateway URL

  def generateSentence(prompt: String, maxTokens: Int): Future[JsValue] = {
    val payload = s"""{
      "prompt": "$prompt",
      "max_tokens": $maxTokens,
      "temperature": 0.7
    }"""

    val httpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$apiUrl/invoke",
      entity = HttpEntity(ContentTypes.`application/json`, payload)
    )

    Http().singleRequest(httpRequest).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[String].map { jsonString =>
            jsonString.parseJson // Return raw JSON as `JsValue`
          }
        case _ =>
          Unmarshal(response.entity).to[String].flatMap { errorBody =>
            Future.failed(new RuntimeException(s"Request failed with status ${response.status}: $errorBody"))
          }
      }
    }
  }
}