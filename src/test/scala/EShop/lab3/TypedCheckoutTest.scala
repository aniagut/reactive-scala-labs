package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  import TypedCheckout._

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val cartProbe = testKit.createTestProbe[TypedCartActor.Command]()
    val orderManagerProbe = testKit.createTestProbe[OrderManager.Command]()

    val checkout = testKit.spawn(new TypedCheckout(cartProbe.ref).start, "checkout")

    checkout ! TypedCheckout.StartCheckout
    checkout ! TypedCheckout.SelectDeliveryMethod("parcel locker")
    checkout ! TypedCheckout.SelectPayment("card", orderManagerProbe.ref)

    val paymentStarted = orderManagerProbe.expectMessageType[OrderManager.ConfirmPaymentStarted]

    paymentStarted.paymentRef ! Payment.DoPayment
    orderManagerProbe.expectMessage(OrderManager.ConfirmPaymentReceived)

    cartProbe.expectMessage(TypedCartActor.ConfirmCheckoutClosed)
  }
}
