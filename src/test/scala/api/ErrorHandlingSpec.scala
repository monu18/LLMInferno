package api

import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.MockitoSugar
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import aws.BedrockClient
import ollama.OllamaClient
import spray.json._
import org.mockito.ArgumentMatchers.{any, anyInt, anyString}
import ch.qos.logback.classic.{Level, Logger}
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class ErrorHandlingSpec extends AsyncWordSpec
  with Matchers
  with MockitoSugar
  with ScalatestRouteTest
  with JsonFormats {

  // Reduce logging noise during tests
  LoggerFactory.getLogger("api.TextGenerationAPI").asInstanceOf[Logger].setLevel(Level.WARN)

  val mockBedrockClient = mock[BedrockClient]
  val mockOllamaClient = mock[OllamaClient]

  val api = new TextGenerationAPI(
    mockBedrockClient,
    mockOllamaClient,
    "test-output"
  )

  // Set up default mock behavior
  when(mockBedrockClient.generateSentence(anyString, anyInt))
    .thenReturn(Future.successful(JsObject(
      "body" -> JsString("""{"response": "Default response"}""")
    )))

  "Error Handling" should {

    "handle service timeouts" in {
      val request = GenerateTextRequest("Test prompt", 100)

      when(mockBedrockClient.generateSentence("Test prompt", 100))
        .thenReturn(Future.failed(new java.util.concurrent.TimeoutException("Request timed out")))

      Post("/api/generate/bedrock", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        val response = responseAs[ErrorResponse]
        response.error should include("Bedrock generation failed")
        succeed
      }
    }

    "handle service unavailable" in {
      val request = GenerateTextRequest("Test prompt", 100)

      when(mockBedrockClient.generateSentence("Test prompt", 100))
        .thenReturn(Future.failed(new RuntimeException("Service unavailable")))

      Post("/api/generate/bedrock", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        val response = responseAs[ErrorResponse]
        response.error should include("Bedrock generation failed")
        succeed
      }
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}