package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import EShop.lab3.Payment.DoPayment
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
    val orderManagerCart = testKit.createTestProbe[TypedCartActor.Event]
    val orderManagerCheckout = testKit.createTestProbe[TypedCheckout.Event]
    val orderManagerPayment = testKit.createTestProbe[Payment.Event]
    val checkout = testKit.spawn(new TypedCheckout(cartProbe.ref, orderManagerCart.ref, orderManagerCheckout.ref, orderManagerPayment.ref).start, "checkout")

    checkout ! TypedCheckout.StartCheckout
    checkout ! TypedCheckout.SelectDeliveryMethod("parcel locker")
    checkout ! TypedCheckout.SelectPayment("card", orderManagerCheckout.ref)

    val paymentStarted = orderManagerCheckout.expectMessageType[TypedCheckout.PaymentStarted]

    paymentStarted.paymentRef ! DoPayment
    orderManagerPayment.expectMessageType[Payment.PaymentReceived]
    cartProbe.expectMessage(TypedCartActor.ConfirmCheckoutClosed)
  }
}
