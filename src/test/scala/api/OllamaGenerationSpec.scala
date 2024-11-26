package api

import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.MockitoSugar
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import aws.BedrockClient
import ollama.OllamaClient

class OllamaGenerationSpec extends AsyncWordSpec
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

  "Ollama Generation API" should {
    "successfully generate text" in {
      val request = GenerateTextRequest("Test prompt", 100)

      when(mockOllamaClient.generateNextQuery(request.prompt))
        .thenReturn("Generated text from Ollama")

      Post("/api/generate/ollama", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[GenerateTextResponse]
        response.text shouldBe "Generated text from Ollama"
      }
    }

    "handle empty responses gracefully" in {
      val request = GenerateTextRequest("Test prompt", 100)

      when(mockOllamaClient.generateNextQuery(request.prompt))
        .thenReturn("")

      Post("/api/generate/ollama", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[GenerateTextResponse]
        response.text shouldBe ""
      }
    }

    "handle exceptions properly" in {
      val request = GenerateTextRequest("Test prompt", 100)

      when(mockOllamaClient.generateNextQuery(request.prompt))
        .thenThrow(new RuntimeException("Ollama service error"))

      Post("/api/generate/ollama", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        val response = responseAs[ErrorResponse]
        response.error should include("Ollama generation failed")
      }
    }
  }
}