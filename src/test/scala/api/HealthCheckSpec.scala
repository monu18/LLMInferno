package api

import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.MockitoSugar
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import aws.BedrockClient
import ollama.OllamaClient
import scala.concurrent.Future

class HealthCheckSpec extends AsyncWordSpec
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

  "Health Check API" should {
    "return OK status for health endpoint" in {
      Get("/api/health") ~> api.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Service is healthy"
      }
    }

    "be accessible under high load" in {
      // Create a sequence of futures, each containing a request
      val futures = (1 to 10).map { _ =>
        Future {
          Get("/api/health") ~> api.routes ~> check {
            responseAs[String]
          }
        }
      }

      // Wait for all futures to complete and check their results
      Future.sequence(futures).map { responses =>
        all(responses) shouldBe "Service is healthy"
      }
    }
  }
}