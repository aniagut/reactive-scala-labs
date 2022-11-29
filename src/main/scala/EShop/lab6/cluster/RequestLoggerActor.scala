package EShop.lab6.cluster

import akka.actor.typed.pubsub.Topic
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior}

object RequestLogger {
  sealed trait Command
  case class LogRequest(brand: String, keywords: List[String]) extends Command
}

object RequestLoggerActor {
  import RequestLogger._

  val RequestLoggerServiceKey = ServiceKey[Command]("RequestLogger")

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      context.system.receptionist ! Receptionist.register(
        RequestLoggerServiceKey,
        context.self)
      val topic = context.spawn(RequestLoggerTopic(), "RequestLoggerTopic")
      val adapter = context.messageAdapter[(String, List[String])] {
        case (brand: String, keywords: List[String]) =>
          LogRequest(brand, keywords)
      }
      topic ! Topic.Subscribe(adapter)
      logRequests()
    }

  def logRequests(): Behavior[Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case LogRequest(brand: String, keywords: List[String]) =>
          context.log.info(
            s"Received request containing brand ${brand} and keywords ${keywords mkString ", "}")
          Behaviors.same
        case other =>
          context.log.warn(s"Unknown message received: $other.")
          Behaviors.stopped
    })
}

object RequestLoggerTopic {
  import RequestLogger._

  def apply(): Behavior[Topic.Command[(String, List[String])]] =
    Topic[(String, List[String])]("request-logger")
}
