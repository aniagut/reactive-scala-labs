package EShop.lab6

import io.gatling.core.Predef.{Simulation, jsonFile, scenario, _}
import io.gatling.http.Predef.http

import scala.concurrent.duration._

class HttpWorkerGatlingTest extends Simulation {

  val httpProtocol = http  //values here are adjusted to cluster_demo.sh script
    .baseUrls("http://localhost:9001", "http://localhost:9002")
    .acceptHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val scn = scenario("BasicSimulation")
    .feed(jsonFile(classOf[HttpWorkerGatlingTest].getResource("/data/product_data.json").getPath).random)
    .exec(
      http("request")
        .get(s"/products?brand=$brand&keywords=$keyword")
        .asJson
    )
    .pause(5)

  setUp(
    scn.inject(
      incrementUsersPerSec(20)
        .times(10)
        .eachLevelLasting(25.seconds)
        .separatedByRampsLasting(1.seconds)
        .startingFrom(100))
  ).protocols(httpProtocol)
}