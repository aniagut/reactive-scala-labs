package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.Payment

object TypedCheckout {

  sealed trait Data

  case object Uninitialized extends Data

  case class SelectingDeliveryStarted(timer: Cancellable) extends Data

  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command

  case object StartCheckout extends Command

  case class SelectDeliveryMethod(method: String) extends Command

  case object CancelCheckout extends Command

  case object ExpireCheckout extends Command

  case class SelectPayment(payment: String, orderManagerCheckoutRef: ActorRef[TypedCheckout.Event]) extends Command

  case object ExpirePayment extends Command

  case object ConfirmPaymentReceived extends Command

  sealed trait Event

  case object CheckOutClosed extends Event

  case class PaymentStarted(paymentRef: ActorRef[Payment.Command]) extends Event
}

class TypedCheckout(cartActor: ActorRef[TypedCartActor.Command],
                    cartAdapter: ActorRef[TypedCartActor.Event] = null,
                    checkoutAdapter: ActorRef[TypedCheckout.Event] = null,
                    paymentAdapter: ActorRef[Payment.Event] = null) {

  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration = 1 seconds

  private def scheduleCheckoutTimer(context: ActorContext[TypedCheckout.Command]): Cancellable = context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)

  private def schedulePaymentTimer(context: ActorContext[TypedCheckout.Command]): Cancellable = context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case StartCheckout =>
          context.log.debug("Checkout started")
          selectingDelivery(scheduleCheckoutTimer(context))
        case other =>
          context.log.warn(s"Unknown message received: $other.")
          Behaviors.same
      }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case SelectDeliveryMethod(method) =>
          context.log.debug(s"Selecting delivery method: $method")
          selectingPaymentMethod(timer)
        case CancelCheckout =>
          timer.cancel()
          context.log.debug("Checkout cancelled")
          cancelled
        case ExpireCheckout =>
          context.log.debug("Checkout timer expired!")
          cancelled
        case other =>
          context.log.warn(s"Unknown message received: $other.")
          Behaviors.same
      }
  )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case SelectPayment(payment: String, orderManagerCheckoutRef: ActorRef[TypedCheckout.Event]) =>
          timer.cancel()
          context.log.debug(s"Selecting payment method: $payment")
          val paymentRef = context.spawn(new Payment(payment, paymentAdapter, context.self, cartAdapter, checkoutAdapter, paymentAdapter).start, "payment")
          orderManagerCheckoutRef ! PaymentStarted(paymentRef)
          processingPayment(schedulePaymentTimer(context))
        case CancelCheckout =>
          timer.cancel()
          context.log.debug("Checkout cancelled")
          cancelled
        case ExpireCheckout =>
          context.log.debug("Checkout timer expired!")
          cancelled
        case other =>
          context.log.warn(s"Unknown message received: $other.")
          Behaviors.same
      }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case ConfirmPaymentReceived =>
          timer.cancel()
          context.log.debug("Payment confirmation received.")
          cartActor ! TypedCartActor.ConfirmCheckoutClosed
          closed
        case CancelCheckout =>
          timer.cancel()
          context.log.debug("Checkout cancelled")
          cartActor ! TypedCartActor.ConfirmCheckoutCancelled
          cancelled
        case ExpirePayment =>
          context.log.debug("Payment timer expired!")
          cartActor ! TypedCartActor.ConfirmCheckoutCancelled
          cancelled
        case other =>
          context.log.warn(s"Unknown message received: $other.")
          Behaviors.same
      }
  )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case _ => context.log.debug("Checkout cancelled.")
          Behaviors.stopped
      }
  )

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case _ => context.log.debug("Checkout closed.")
          Behaviors.stopped
      }
  )
}
