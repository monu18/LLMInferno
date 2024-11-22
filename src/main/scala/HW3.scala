import akka.actor.ActorSystem
import akka.stream.Materializer
import aws.BedrockClient
import aws.JsonProtocol.StringJsonFormat
import ollama.OllamaClient
import spray.json._

import java.io.PrintWriter
import java.nio.file.{Files, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, blocking}

object HW3 extends App {
  implicit val system: ActorSystem = ActorSystem("ConversationalAgentSystem")
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher

  val bedrockClient = new BedrockClient()
  val ollamaClient = new OllamaClient()

  // Define the initial prompt
  val initialPrompt = "Teach me about cloud computing"

  // Number of exchanges (termination condition)
  val maxExchanges = 5

  // Ensure the output directory exists
  val outputDir = Paths.get("src/main/resources/output")
  if (!Files.exists(outputDir)) {
    Files.createDirectories(outputDir)
  }

  // Log file setup
  val logFilePath = outputDir.resolve("conversation_log.txt").toFile
  val writer = new PrintWriter(logFilePath)

  def logResponse(agent: String, response: String): Unit = {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    writer.println(s"[$timestamp] $agent: $response")
    writer.println()
    writer.flush()
  }

  // Simulate conversation
  def simulateConversation(initialPrompt: String, maxExchanges: Int): Unit = {
    println(s"Starting conversation with initial prompt: $initialPrompt")
    logResponse("User", initialPrompt)

    var currentPrompt = initialPrompt
    var exchangeCount = 0

    while (exchangeCount < maxExchanges) {
      println(s"\nExchange #${exchangeCount + 1}")

      // Add delay between requests
//      blocking(Thread.sleep(20000)) // 20 seconds delay

      // Get response from Bedrock
      val bedrockFuture: Future[String] = bedrockClient.generateSentence(currentPrompt, 100).map { jsValue =>
        println(s"Raw response from Bedrock: ${jsValue.prettyPrint}")
        val responseJson = jsValue.asJsObject
        val responseBody = responseJson.fields("body").convertTo[String]
        val parsedBody = responseBody.parseJson.asJsObject
        parsedBody.fields("response").convertTo[String]
      }

      val bedrockResponse = Await.result(bedrockFuture, 60.seconds)

      if (bedrockResponse.trim.isEmpty) {
        println("Bedrock response is empty. Ending conversation.")
        logResponse("System", "Bedrock response is empty. Conversation terminated.")
        return
      }

      println(s"Bedrock Response: $bedrockResponse")
      logResponse("Bedrock", bedrockResponse)

      // Use Bedrock response as input to Ollama
      val ollamaResponse = ollamaClient.generateNextQuery(bedrockResponse)
      println(s"Ollama Response: $ollamaResponse")
      logResponse("Ollama", ollamaResponse)

      // Update prompt for the next exchange
      currentPrompt = ollamaResponse
      exchangeCount += 1
    }

    println("\nConversation ended.")
  }

  // Run the simulation
  simulateConversation(initialPrompt, maxExchanges)

  // Close the writer and terminate the system
  writer.close()
  system.terminate()
}