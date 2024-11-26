package api

import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.MockitoSugar
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import aws.BedrockClient
import ollama.OllamaClient
import spray.json._
import org.mockito.ArgumentMatchers.{any, anyString, anyInt}
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll}

import scala.concurrent.Future
import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable.ListBuffer

class ConversationSpec extends AsyncWordSpec
  with Matchers
  with MockitoSugar
  with ScalatestRouteTest
  with JsonFormats
  with BeforeAndAfterEach
  with BeforeAndAfterAll {

  val mockBedrockClient = mock[BedrockClient]
  val mockOllamaClient = mock[OllamaClient]
  val testOutputDir = "test-output"
  private val createdFiles = ListBuffer[Path]()

  val api = new TextGenerationAPI(
    mockBedrockClient,
    mockOllamaClient,
    testOutputDir
  )

  // Ensure test output directory exists
  Files.createDirectories(Paths.get(testOutputDir))

  override def beforeEach(): Unit = {
    super.beforeEach()
    // Reset mocks before each test
    reset(mockBedrockClient, mockOllamaClient)

    // Set up default mock behavior
    when(mockBedrockClient.generateSentence(anyString, anyInt))
      .thenReturn(Future.successful(JsObject(
        "body" -> JsString("""{"response": "Default response"}""")
      )))

    when(mockOllamaClient.generateNextQuery(anyString))
      .thenReturn("Default Ollama response")
  }

  override def afterEach(): Unit = {
    // Clean up any files created during the test
    createdFiles.foreach(path => Files.deleteIfExists(path))
    createdFiles.clear()
    super.afterEach()
  }

  override def afterAll(): Unit = {
    // Clean up test output directory
    val directory = Paths.get(testOutputDir)
    if (Files.exists(directory)) {
      Files.list(directory).forEach(Files.deleteIfExists(_))
      Files.deleteIfExists(directory)
    }
    system.terminate()
    super.afterAll()
  }

  "Conversation API" should {
    "generate a complete conversation chain" in {
      val request = ConversationRequest(
        prompt = "Initial prompt",
        maxTokens = 100,
        maxExchanges = 2,
        saveToFile = false
      )

      val bedrockResponse = JsObject(
        "body" -> JsString("""{"response": "Bedrock response"}""")
      )

      when(mockBedrockClient.generateSentence(anyString, anyInt))
        .thenReturn(Future.successful(bedrockResponse))

      when(mockOllamaClient.generateNextQuery(anyString))
        .thenReturn("Ollama response")

      Post("/api/generate/conversation", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ConversationResponse]
        response.conversation.size shouldBe 5
        response.conversation.map(_.agent) should contain allOf("User", "Bedrock", "Ollama")
        response.filePath shouldBe None
      }

      Future.successful(succeed)
    }

    "save conversation to file when requested" in {
      val request = ConversationRequest(
        prompt = "Initial prompt",
        maxTokens = 100,
        maxExchanges = 1,
        saveToFile = true
      )

      val bedrockResponse = JsObject(
        "body" -> JsString("""{"response": "Bedrock response"}""")
      )

      when(mockBedrockClient.generateSentence(anyString, anyInt))
        .thenReturn(Future.successful(bedrockResponse))

      when(mockOllamaClient.generateNextQuery(anyString))
        .thenReturn("Ollama response")

      val result = Post("/api/generate/conversation", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ConversationResponse]
        response.filePath shouldBe defined

        val filePath = Paths.get(response.filePath.get)
        createdFiles += filePath // Add to cleanup list

        Files.exists(filePath) shouldBe true
        succeed
      }

      result
    }

    "handle Ollama errors properly" in {
      val request = ConversationRequest(
        prompt = "Initial prompt",
        maxTokens = 100,
        maxExchanges = 1,
        saveToFile = false
      )

      val bedrockResponse = JsObject(
        "body" -> JsString("""{"response": "Bedrock response"}""")
      )

      when(mockBedrockClient.generateSentence(anyString, anyInt))
        .thenReturn(Future.successful(bedrockResponse))

      when(mockOllamaClient.generateNextQuery(anyString))
        .thenThrow(new RuntimeException("Ollama error"))

      Post("/api/generate/conversation", request) ~> api.routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ConversationResponse]
        response.conversation.size should be > 0
        succeed
      }

      Future.successful(succeed)
    }
  }
}