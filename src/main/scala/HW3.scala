import akka.actor.ActorSystem
import akka.stream.Materializer
import aws.BedrockClient
import aws.JsonProtocol.StringJsonFormat
import ollama.OllamaClient
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object HW3 extends App {
  implicit val system: ActorSystem = ActorSystem("ConversationalAgentSystem")
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher

  val bedrockClient = new BedrockClient()
  val ollamaClient = new OllamaClient()

  // Define the initial prompt
  val initialPrompt = "What is the future of AI?"

  // Number of exchanges (termination condition)
  val maxExchanges = 5

  // Simulate conversation
  def simulateConversation(initialPrompt: String, maxExchanges: Int): Unit = {
    println(s"Starting conversation with initial prompt: $initialPrompt")

    var currentPrompt = initialPrompt
    var exchangeCount = 0

    while (exchangeCount < maxExchanges) {
      println(s"\nExchange #${exchangeCount + 1}")

      // Get response from Bedrock
      val bedrockFuture: Future[String] = bedrockClient.generateSentence(currentPrompt, 100).map { jsValue =>
        // Parse the response structure
        val responseJson = jsValue.asJsObject
        val responseBody = responseJson.fields("body").convertTo[String]
        val parsedBody = responseBody.parseJson.asJsObject
        parsedBody.fields("response").convertTo[String]
      }

      val bedrockResponse = Await.result(bedrockFuture, 20.seconds)
      println(s"Bedrock Response: $bedrockResponse")

      // Use Bedrock response as input to Ollama
      val ollamaResponse = ollamaClient.generateNextQuery(bedrockResponse)
      println(s"Ollama Response: $ollamaResponse")

      // Update prompt for the next exchange
      currentPrompt = ollamaResponse
      exchangeCount += 1
    }

    println("\nConversation ended.")
  }

  // Run the simulation
  simulateConversation(initialPrompt, maxExchanges)

  // Terminate the system
  system.terminate()
}