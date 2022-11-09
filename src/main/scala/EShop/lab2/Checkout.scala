package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data

  case object Uninitialized extends Data

  case class SelectingDeliveryStarted(timer: Cancellable) extends Data

  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command

  case object StartCheckout extends Command

  case class SelectDeliveryMethod(method: String) extends Command

  case object CancelCheckout extends Command

  case object ExpireCheckout extends Command

  case class SelectPayment(payment: String) extends Command

  case object ExpirePayment extends Command

  case object ConfirmPaymentReceived extends Command

  sealed trait Event

  case object CheckOutClosed extends Event

  case class PaymentStarted(payment: ActorRef) extends Event

}

class Checkout extends Actor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  private def scheduleCheckoutTimer: Cancellable = scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)

  private def schedulePaymentTimer: Cancellable =
    context.system.scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      log.debug("Checkout started.")
      context become selectingDelivery(scheduleCheckoutTimer)
    case other =>
      log.warning(s"Unknown message received: $other.")
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) =>
      log.debug(f"Selecting delivery method: $method")
      context become selectingPaymentMethod(timer)
    case CancelCheckout =>
      timer.cancel()
      log.debug("Checkout cancelling...")
      context become cancelled
    case ExpireCheckout =>
      log.debug("Timer expired!")
      context become cancelled
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(payment) =>
      timer.cancel()
      log.debug(s"Selecting payment method: $payment")
      context become processingPayment(schedulePaymentTimer)
    case CancelCheckout =>
      timer.cancel()
      log.debug("Checkout cancelling...")
      context become cancelled
    case ExpireCheckout =>
      log.debug("Checkout timer expired!")
      context become cancelled
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ConfirmPaymentReceived =>
      log.debug("Payment confirmation received.")
      context become closed
    case CancelCheckout =>
      timer.cancel()
      log.debug("Checkout cancelling...")
      context become cancelled
    case ExpirePayment =>
      log.debug("Payment timer expired!")
      context become cancelled

  }

  def cancelled: Receive = LoggingReceive { case _ =>
    log.debug("Checkout cancelled.")
  }

  def closed: Receive = LoggingReceive { case _ =>
    log.debug("Checkout closed.")
  }
}
