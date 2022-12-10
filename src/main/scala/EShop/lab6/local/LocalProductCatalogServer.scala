package EShop.lab6.local
import EShop.lab5.{ProductCatalog, ProductCatalogJsonSupport, SearchService}
import akka.actor.typed.scaladsl.Routers
import akka.http.scaladsl.server.Directives.{parameters, path}
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import akka.http.scaladsl.server.directives.RouteDirectives.complete

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

class LocalProductCatalogServer extends ProductCatalogJsonSupport {
  implicit val system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "LocalReactiveRouters")
  implicit val executionContext: ExecutionContextExecutor =
    system.executionContext
  implicit val timeout: Timeout = 5.seconds
  implicit val scheduler: Scheduler = system.scheduler

  val workers: ActorRef[ProductCatalog.Query] =
    system.systemActorOf(Routers.pool(5)(ProductCatalog(new SearchService())),
                         "workersRouter")

  def routes: Route = {
    path("products") {
      get {
        parameters("brand".as[String], "keywords".as[String]) {
          (brand, keywords) =>
            println(brand)
            complete {
              val items = workers
                .ask(ref =>
                  ProductCatalog
                    .GetItems(brand, keywords.split(" ").toList, ref))
                .mapTo[ProductCatalog.Items]
              Future.successful(items)
            }
        }
      }
    }
  }

  def run(port: Int): Unit = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.ready(bindingFuture, Duration.Inf)
  }
}

object LocalProductCatalogServerApp extends App {
  val localProductCatalogsAppServer = new LocalProductCatalogServer()
  localProductCatalogsAppServer.run(Try(args(0).toInt).getOrElse(9000))
}
