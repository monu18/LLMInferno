import akka.actor.ActorSystem
import akka.stream.Materializer
import org.apache.log4j.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object HW3 extends App {
  val logger = Logger.getLogger(getClass.getName)
  implicit val system: ActorSystem = ActorSystem("BedrockClientSystem")
  implicit val materializer: Materializer = Materializer(system)

  val bedrockClient = new aws.BedrockClient()

  // Call the `generateSentence` method
  val futureResponse = bedrockClient.generateSentence("The stars are bright tonight.", 50)

  // Handle the Future response
  futureResponse.onComplete {
    case Success(sentences) =>
      logger.info(s"Generated sentences: $sentences")
    case Failure(exception) =>
      logger.error(s"Failed to generate sentence: ${exception.getMessage}")
  }

  // Keep the application alive to wait for the response
  Thread.sleep(5000) // Increase if needed based on your response time
  system.terminate()
}