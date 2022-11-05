package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

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

  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command

  case object ExpirePayment extends Command

  case object ConfirmPaymentReceived extends Command

  sealed trait Event

  case object CheckOutClosed extends Event

  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event
  case object CheckoutStarted                                   extends Event
  case object CheckoutCancelled                                 extends Event
  case class DeliveryMethodSelected(method: String)             extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(Some(timer))
  case class SelectingPaymentMethod(timer: Cancellable) extends State(Some(timer))
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(Some(timer))
}

class TypedCheckout(cartActor: ActorRef[TypedCartActor.Command]) {

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
        case SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) =>
          timer.cancel()
          context.log.debug(s"Selecting payment method: $payment")
          val paymentRef = context.spawn(new Payment(payment, orderManagerRef, context.self).start, "payment")
          orderManagerRef ! OrderManager.ConfirmPaymentStarted(paymentRef)
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
