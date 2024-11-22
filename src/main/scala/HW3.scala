import akka.actor.ActorSystem
import akka.stream.Materializer
import aws.BedrockClient
import aws.JsonProtocol.StringJsonFormat
import com.typesafe.config.{Config, ConfigFactory}
import ollama.OllamaClient
import spray.json._
import org.slf4j.LoggerFactory

import java.io.PrintWriter
import java.nio.file.{Files, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, blocking}

object HW3 {
  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    val config: Config = ConfigFactory.load()

    // Configuration with defaults
    val outputPath = config.getString("app.outputDir")
    val maxExchanges = config.getInt("app.maxExchanges")
    val initialPrompt: String =
      if (args.nonEmpty) args.mkString(" ")
      else {
        logger.error("No initial prompt provided as a command-line argument.")
        sys.exit(1)
      }

    implicit val system: ActorSystem = ActorSystem("ConversationalAgentSystem")
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher

    logger.info("Application started with configuration:")
    logger.info(s"Output Directory: $outputPath")
    logger.info(s"Max Exchanges: $maxExchanges")
    logger.info(s"Initial Prompt: $initialPrompt")

    val bedrockClient = new BedrockClient()
    val ollamaClient = new OllamaClient()

    // Ensure the output directory exists
    val outputDir = Paths.get(outputPath)
    if (!Files.exists(outputDir)) {
      Files.createDirectories(outputDir)
      logger.info(s"Output directory created at $outputDir")
    }

    // Log file setup
    val logFilePath = outputDir.resolve("conversation_log.txt").toFile
    val writer = new PrintWriter(logFilePath)
    logger.info(s"Log file initialized at $logFilePath")

    def logResponse(agent: String, response: String): Unit = {
      val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
      writer.println(s"[$timestamp] $agent: $response")
      writer.println()
      writer.flush()
      logger.debug(s"Logged response for $agent: $response")
    }

    // Simulate conversation
    def simulateConversation(initialPrompt: String, maxExchanges: Int): Unit = {
      logger.info(s"Starting conversation with initial prompt: $initialPrompt")
      logResponse("User", initialPrompt)

      var currentPrompt = initialPrompt
      var exchangeCount = 0

      while (exchangeCount < maxExchanges) {
        logger.info(s"Starting exchange #${exchangeCount + 1}")

        try {
          // Get response from Bedrock
          val bedrockFuture: Future[String] = bedrockClient.generateSentence(currentPrompt, 100).map { jsValue =>
            logger.debug(s"Raw response from Bedrock: ${jsValue.prettyPrint}")
            val responseJson = jsValue.asJsObject
            val responseBody = responseJson.fields("body").convertTo[String]
            val parsedBody = responseBody.parseJson.asJsObject
            parsedBody.fields("response").convertTo[String]
          }

          val bedrockResponse = Await.result(bedrockFuture, 60.seconds)

          if (bedrockResponse.trim.isEmpty) {
            logger.warn("Bedrock response is empty. Ending conversation.")
            logResponse("System", "Bedrock response is empty. Conversation terminated.")
            return
          }

          logger.info(s"Bedrock Response: $bedrockResponse")
          logResponse("Bedrock", bedrockResponse)

          // Use Bedrock response as input to Ollama
          val ollamaResponse = ollamaClient.generateNextQuery(bedrockResponse)
          logger.info(s"Ollama Response: $ollamaResponse")
          logResponse("Ollama", ollamaResponse)

          // Update prompt for the next exchange
          currentPrompt = ollamaResponse
          exchangeCount += 1
        } catch {
          case ex: Exception =>
            logger.error(s"Error during exchange #${exchangeCount + 1}: ${ex.getMessage}", ex)
            logResponse("System", s"Error occurred: ${ex.getMessage}")
            return
        }
      }

      logger.info("Conversation ended.")
    }

    // Run the simulation
    simulateConversation(initialPrompt, maxExchanges)

    // Close the writer and terminate the system
    writer.close()
    logger.info("Log file closed.")
    system.terminate()
    logger.info("Actor system terminated.")
  }
}