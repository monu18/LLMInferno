package ollama

import io.github.ollama4j.OllamaAPI
import com.typesafe.config.ConfigFactory

class OllamaClient {
  private val config = ConfigFactory.load()
  private val host = config.getString("ollama.host")
  private val model = config.getString("ollama.model")
  private val api = new OllamaAPI(host)

  def generateNextQuery(previousResponse: String): String = {
    val prompt = s"how can you respond to the statement: $previousResponse"
    api.generate(model, prompt, false, null).getResponse
  }
}