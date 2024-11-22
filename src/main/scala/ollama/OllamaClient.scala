package ollama

import com.typesafe.config.ConfigFactory
import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.utils.Options
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._

class OllamaClient {
  private val logger = LoggerFactory.getLogger(getClass)
  private val config = ConfigFactory.load()

  // Load configuration
  private val host = config.getString("ollama.host")
  private val model = config.getString("ollama.model")
  private val api = new OllamaAPI(host)

  api.setRequestTimeoutSeconds(120) // Set timeout to 120 seconds

  def generateNextQuery(previousResponse: String): String = {
    logger.info(s"Generating response for previous input: $previousResponse")

    // Update the prompt to request concise responses
    val prompt = s"Please respond concisely in 50-60 words: $previousResponse"
    logger.debug(s"Generated prompt: $prompt")

    val options = new Options(Map[String, AnyRef](
      "temperature" -> java.lang.Double.valueOf(0.7), // Control creativity
      "max_tokens" -> java.lang.Integer.valueOf(60)   // Limit the token count
    ).asJava)

    try {
      val response = api.generate(model, prompt, false, options).getResponse
      logger.info(s"Received response: $response")
      response
    } catch {
      case ex: Exception =>
        logger.error(s"Error generating response: ${ex.getMessage}", ex)
        throw ex
    }
  }
}

object OllamaClientApp extends App {
  private val logger = LoggerFactory.getLogger(getClass)

  // Instantiate the OllamaClient
  val ollamaClient = new OllamaClient

  // Test input
  val previousResponse = "The stars are bright tonight."

  try {
    // Call the client and log the result
    val response = ollamaClient.generateNextQuery(previousResponse)
    logger.info("--------------")
    logger.info(s"Response from Ollama: $response")
    logger.info("--------------")
  } catch {
    case e: Exception =>
      logger.error(s"Error occurred during Ollama query: ${e.getMessage}", e)
  }
}