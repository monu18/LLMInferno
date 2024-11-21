package ollama

import com.typesafe.config.ConfigFactory
import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.utils.Options
import scala.jdk.CollectionConverters._

import java.util

class OllamaClient {
  private val config = ConfigFactory.load()
  private val host = config.getString("ollama.host")
  private val model = config.getString("ollama.model")
  private val api = new OllamaAPI(host)
  api.setRequestTimeoutSeconds(120) // Set timeout to 120 seconds

  def generateNextQuery(previousResponse: String): String = {
    val prompt = s"how can you respond to the statement: $previousResponse"
    val options = new Options(Map[String, AnyRef](
      "temperature" -> java.lang.Double.valueOf(0.7), // Adjust the temperature
      "max_tokens" -> java.lang.Integer.valueOf(50)   // Limit the token count
    ).asJava)
    api.generate(model, prompt, false, options).getResponse
  }
}

object OllamaClientApp extends App {
  // Instantiate the OllamaClient
  val ollamaClient = new OllamaClient

  // Test input
  val previousResponse = "The stars are bright tonight."

  try {
    // Call the client and print the result
    val response = ollamaClient.generateNextQuery(previousResponse)
    println(s"Response from Ollama: $response")
  } catch {
    case e: Exception =>
      println(s"Error occurred: ${e.getMessage}")
      e.printStackTrace()
  }
}