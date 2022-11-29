package EShop.lab6.cluster

import akka.actor.typed.pubsub.Topic
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

object RequestNode extends App {
  private val config = ConfigFactory.load()
  val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalog",
    config.getConfig(Try(args(0)).getOrElse("seed-node3")).withFallback(config)
  )

  system.systemActorOf(RequestCounterActor(), "requestCounter")
  system.systemActorOf(RequestLoggerActor(), "requestLogger")

  Await.ready(system.whenTerminated, Duration.Inf)
}

object RequestCounter {
  sealed trait Command
  case class ProductRequestsCount(replyTo: ActorRef[Int]) extends Command
  case object IncrementCounter extends Command
}

object RequestCounterActor {
  import RequestCounter._

  val RequestCounterServiceKey = ServiceKey[Command]("RequestCounter")

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      context.system.receptionist ! Receptionist.register(
        RequestCounterServiceKey,
        context.self)
      val topic = context.spawn(RequestCounterTopic(), "RequestCounterTopic")
      val adapter = context.messageAdapter[String] {
        case id: String => IncrementCounter
      }

      topic ! Topic.Subscribe(adapter)
      countRequests(0)
    }

  def countRequests(state: Int): Behavior[Command] =
    Behaviors.receive((context, message) => {
      message match {
        case IncrementCounter =>
          countRequests(state + 1)
        case ProductRequestsCount(replyTo) =>
          replyTo ! state
          Behaviors.same
      }
    })
}

object RequestCounterTopic {
  import RequestCounter._

  def apply(): Behavior[Topic.Command[String]] =
    Topic[String]("request-counter")
}
