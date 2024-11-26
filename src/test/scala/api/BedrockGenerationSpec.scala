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

class BedrockGenerationSpec extends AsyncWordSpec
  with Matchers
  with MockitoSugar
  with ScalatestRouteTest
  with JsonFormats {

  val mockBedrockClient = mock[BedrockClient]
  val mockOllamaClient = mock[OllamaClient]

  // Set up default mock behavior
  when(mockBedrockClient.generateSentence(anyString, anyInt))
    .thenReturn(Future.successful(JsObject(
      "body" -> JsString("""{"response": "Default response"}""")
    )))

  val api = new TextGenerationAPI(
    mockBedrockClient,
    mockOllamaClient,
    "test-output"
  )

  "Bedrock Generation API" should {
    "successfully generate text" in {
      val request = GenerateTextRequest("Test prompt", 100)
      val expectedResponse = JsObject(
        "body" -> JsString("""{"response": "Generated text from Bedrock"}""")
      )

      when(mockBedrockClient.generateSentence("Test prompt", 100))
        .thenReturn(Future.successful(expectedResponse))

      Post("/api/generate/bedrock", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[GenerateTextResponse]
        response.text shouldBe "Generated text from Bedrock"
      }
    }

    "handle empty prompts gracefully" in {
      val request = GenerateTextRequest("", 100)
      val expectedResponse = JsObject(
        "body" -> JsString("""{"response": "I need a prompt to generate text"}""")
      )

      when(mockBedrockClient.generateSentence("", 100))
        .thenReturn(Future.successful(expectedResponse))

      Post("/api/generate/bedrock", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[GenerateTextResponse]
        response.text shouldBe "I need a prompt to generate text"
      }
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}