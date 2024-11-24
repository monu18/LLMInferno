package api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import aws.BedrockClient
import ollama.OllamaClient
import spray.json._

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// Request/Response models
final case class GenerateTextRequest(prompt: String, maxTokens: Int)
final case class ConversationRequest(
                                      prompt: String,
                                      maxTokens: Int,
                                      maxExchanges: Int,
                                      saveToFile: Boolean = false
                                    )
final case class ConversationMessage(timestamp: String, agent: String, message: String)
final case class GenerateTextResponse(text: String)
final case class ConversationResponse(
                                       conversation: Seq[ConversationMessage],
                                       filePath: Option[String] = None
                                     )
final case class ErrorResponse(error: String)

// JSON formatting
trait JsonFormats extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val generateTextRequestFormat = jsonFormat2(GenerateTextRequest)
  implicit val conversationRequestFormat = jsonFormat4(ConversationRequest)
  implicit val conversationMessageFormat = jsonFormat3(ConversationMessage)
  implicit val generateTextResponseFormat = jsonFormat1(GenerateTextResponse)
  implicit val conversationResponseFormat = jsonFormat2(ConversationResponse)
  implicit val errorResponseFormat = jsonFormat1(ErrorResponse)
}

class TextGenerationAPI(
                         bedrockClient: BedrockClient,
                         ollamaClient: OllamaClient,
                         outputDir: String = "output"
                       )(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends JsonFormats {

  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  // Ensure output directory exists
  private val outputPath = Paths.get(outputDir)
  if (!Files.exists(outputPath)) {
    Files.createDirectories(outputPath)
    logger.info(s"Created output directory at $outputPath")
  }

  val routes = {
    pathPrefix("api") {
      // Health check endpoint
      path("health") {
        get {
          complete(StatusCodes.OK -> "Service is healthy")
        }
      } ~
        // Bedrock endpoint
        path("generate" / "bedrock") {
          post {
            entity(as[GenerateTextRequest]) { request =>
              onComplete(generateBedrockText(request)) {
                case Success(response) => complete(StatusCodes.OK -> response)
                case Failure(ex) =>
                  logger.error("Bedrock generation failed", ex)
                  complete(StatusCodes.InternalServerError ->
                    ErrorResponse(s"Bedrock generation failed: ${ex.getMessage}"))
              }
            }
          }
        } ~
        // Ollama endpoint
        path("generate" / "ollama") {
          post {
            entity(as[GenerateTextRequest]) { request =>
              onComplete(generateOllamaText(request)) {
                case Success(response) => complete(StatusCodes.OK -> response)
                case Failure(ex) =>
                  logger.error("Ollama generation failed", ex)
                  complete(StatusCodes.InternalServerError ->
                    ErrorResponse(s"Ollama generation failed: ${ex.getMessage}"))
              }
            }
          }
        } ~
        // Conversation chain endpoint
        path("generate" / "conversation") {
          post {
            entity(as[ConversationRequest]) { request =>
              onComplete(generateConversationChain(request)) {
                case Success(response) => complete(StatusCodes.OK -> response)
                case Failure(ex) =>
                  logger.error("Conversation chain generation failed", ex)
                  complete(StatusCodes.InternalServerError ->
                    ErrorResponse(s"Conversation generation failed: ${ex.getMessage}"))
              }
            }
          }
        }
    }
  }

  private def getCurrentTimestamp: String = {
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
  }

  private def createMessage(agent: String, content: String): ConversationMessage = {
    ConversationMessage(getCurrentTimestamp, agent, content)
  }

  private def generateBedrockText(request: GenerateTextRequest): Future[GenerateTextResponse] = {
    bedrockClient.generateSentence(request.prompt, request.maxTokens).map { json =>
      val responseBody = json.asJsObject.fields("body").convertTo[String]
      val parsedBody = responseBody.parseJson.asJsObject
      val response = parsedBody.fields("response").convertTo[String]
      GenerateTextResponse(response)
    }
  }

  private def generateOllamaText(request: GenerateTextRequest): Future[GenerateTextResponse] = {
    Future {
      val response = ollamaClient.generateNextQuery(request.prompt)
      GenerateTextResponse(response)
    }
  }

  private def saveConversationToFile(conversation: Seq[ConversationMessage]): String = {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val filePath = outputPath.resolve(s"conversation_$timestamp.txt").toString

    val writer = new PrintWriter(new File(filePath))
    try {
      conversation.foreach { message =>
        writer.println(s"[${message.timestamp}] ${message.agent}: ${message.message}")
        writer.println()
      }
      logger.info(s"Conversation saved to $filePath")
      filePath
    } finally {
      writer.close()
    }
  }

  private def generateConversationChain(request: ConversationRequest): Future[ConversationResponse] = {
    var conversation = Vector[ConversationMessage]()
    conversation = conversation :+ createMessage("User", request.prompt)

    def runExchange(currentPrompt: String, exchangesLeft: Int): Future[Vector[ConversationMessage]] = {
      if (exchangesLeft <= 0) {
        Future.successful(conversation)
      } else {
        // Generate Bedrock response
        bedrockClient.generateSentence(currentPrompt, request.maxTokens).flatMap { json =>
          val responseBody = json.asJsObject.fields("body").convertTo[String]
          val parsedBody = responseBody.parseJson.asJsObject
          val bedrockResponse = parsedBody.fields("response").convertTo[String]

          if (bedrockResponse.trim.isEmpty) {
            logger.warn("Bedrock response is empty")
            Future.successful(conversation)
          } else {
            // Add Bedrock response to conversation
            conversation = conversation :+ createMessage("Bedrock", bedrockResponse)

            // Generate Ollama response
            try {
              val ollamaResponse = ollamaClient.generateNextQuery(bedrockResponse)
              if (ollamaResponse.trim.isEmpty) {
                logger.warn("Ollama response is empty")
                Future.successful(conversation)
              } else {
                // Add Ollama response to conversation
                conversation = conversation :+ createMessage("Ollama", ollamaResponse)
                // Continue with next exchange
                runExchange(ollamaResponse, exchangesLeft - 1)
              }
            } catch {
              case ex: Exception =>
                logger.error("Error in Ollama generation", ex)
                Future.successful(conversation)
            }
          }
        }.recoverWith { case ex =>
          logger.error("Error in Bedrock generation", ex)
          Future.successful(conversation)
        }
      }
    }

    runExchange(request.prompt, request.maxExchanges).map { finalConversation =>
      if (request.saveToFile) {
        val filePath = saveConversationToFile(finalConversation)
        ConversationResponse(finalConversation, Some(filePath))
      } else {
        ConversationResponse(finalConversation, None)
      }
    }
  }

  def start(host: String, port: Int): Future[Http.ServerBinding] = {
    Http().newServerAt(host, port).bind(routes)
  }
}

object TextGenerationAPIServer extends App {
  implicit val system = ActorSystem("TextGenerationAPI")
  implicit val materializer = Materializer(system)
  implicit val executionContext = system.dispatcher

  val config = com.typesafe.config.ConfigFactory.load()
  val outputDir = config.getString("app.outputDir")

  val bedrockClient = new BedrockClient()
  val ollamaClient = new OllamaClient()
  val api = new TextGenerationAPI(bedrockClient, ollamaClient, outputDir)

  val host = "0.0.0.0"
  val port = 8080

  api.start(host, port).onComplete {
    case Success(binding) =>
      println(s"Server started at http://$host:$port/")
    case Failure(ex) =>
      println(s"Failed to start server: ${ex.getMessage}")
      system.terminate()
  }

  sys.addShutdownHook {
    system.terminate()
  }
}