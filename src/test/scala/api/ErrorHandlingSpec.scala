package api

import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.MockitoSugar
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import aws.BedrockClient
import ollama.OllamaClient
import spray.json._
import scala.concurrent.Future
import org.mockito.ArgumentMatchers.{any, anyInt, anyString}

class ErrorHandlingSpec extends AsyncWordSpec
  with Matchers
  with MockitoSugar
  with ScalatestRouteTest
  with JsonFormats {

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

    "handle negative token counts" in {
      val request = GenerateTextRequest("Test prompt", -1)

      // Explicitly stub for negative token count
      when(mockBedrockClient.generateSentence("Test prompt", -1))
        .thenReturn(Future.failed(new IllegalArgumentException("Token count must be positive")))

      Post("/api/generate/bedrock", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        val response = responseAs[ErrorResponse]
        response.error should include("Bedrock generation failed")
      }
    }

    "handle concurrent failures gracefully" in {
      val request = GenerateTextRequest("Test prompt", 100)

      when(mockBedrockClient.generateSentence("Test prompt", 100))
        .thenReturn(Future.failed(new RuntimeException("Service timeout")))

      val futures = (1 to 5).map { _ =>
        Post("/api/generate/bedrock", request) ~> api.routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          responseAs[ErrorResponse]
        }
      }

      Future.sequence(futures.map(Future.successful)).map { responses =>
        all(responses.map(_.error)) should include("Bedrock generation failed")
      }
    }

    "handle timeouts appropriately" in {
      val request = GenerateTextRequest("Test prompt", 100)

      when(mockBedrockClient.generateSentence("Test prompt", 100))
        .thenReturn(Future.failed(new java.util.concurrent.TimeoutException("Request timed out")))

      Post("/api/generate/bedrock", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        val response = responseAs[ErrorResponse]
        response.error should include("Bedrock generation failed")
      }
    }

    "handle service unavailable errors" in {
      val request = GenerateTextRequest("Test prompt", 100)

      when(mockBedrockClient.generateSentence("Test prompt", 100))
        .thenReturn(Future.failed(new RuntimeException("Service unavailable")))

      Post("/api/generate/bedrock", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        val response = responseAs[ErrorResponse]
        response.error should include("Bedrock generation failed")
      }
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}