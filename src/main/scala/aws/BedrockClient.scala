package aws

import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.{InvokeRequest, InvokeResponse}
import software.amazon.awssdk.core.SdkBytes
import com.typesafe.config.ConfigFactory
import spray.json._
import scala.util.{Try, Success, Failure}

// JSON Protocol for serialization/deserialization
object JsonProtocol extends DefaultJsonProtocol {
  implicit val seqStringFormat: JsonFormat[Seq[String]] = seqFormat[String]
}

class BedrockClient {
  import JsonProtocol._

  private val config = ConfigFactory.load()
  private val lambdaClient = LambdaClient.create()
  private val functionName = config.getString("aws.lambda.functionName")

  def generateSentence(query: String, maxLength: Int): Seq[String] = {
    val payload = s"""{"query":"$query", "maxLength":$maxLength}"""
    val request = InvokeRequest.builder()
      .functionName(functionName)
      .payload(SdkBytes.fromUtf8String(payload)) // Convert payload to SdkBytes
      .build()

    val response = Try(lambdaClient.invoke(request))
    parseSentenceResponse(response.map(_.payload().asUtf8String()))
  }

  def generateNextWord(context: Seq[String]): String = {
    val payload = s"""{"context":${context.toJson}}"""
    val request = InvokeRequest.builder()
      .functionName(functionName)
      .payload(SdkBytes.fromUtf8String(payload)) // Convert payload to SdkBytes
      .build()

    val response = Try(lambdaClient.invoke(request))
    parseWordResponse(response.map(_.payload().asUtf8String()))
  }

  private def parseSentenceResponse(response: Try[String]): Seq[String] = response match {
    case Success(json) => json.parseJson.convertTo[Seq[String]]
    case Failure(ex) => throw new RuntimeException(s"Error invoking AWS Lambda: ${ex.getMessage}")
  }

  private def parseWordResponse(response: Try[String]): String = response match {
    case Success(json) => json.parseJson.convertTo[String]
    case Failure(ex) => throw new RuntimeException(s"Error invoking AWS Lambda: ${ex.getMessage}")
  }
}