package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command

  case class AddItem(item: Any) extends Command

  case class RemoveItem(item: Any) extends Command

  case object ExpireCart extends Command

  case object StartCheckout extends Command

  case object ConfirmCheckoutCancelled extends Command

  case object ConfirmCheckoutClosed extends Command

  sealed trait Event

  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      log.debug(s"Adding $item to the cart.")
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
    case other =>
      log.warning(s"Unknown message received: $other.")
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      log.debug(s"Adding $item to the cart.")
      context become nonEmpty(cart.addItem(item), scheduleTimer)
    case RemoveItem(item) if cart.contains(item) && cart.size == 1 =>
      timer.cancel()
      log.debug(s"Removing $item from cart.")
      context become empty
    case RemoveItem(item) if cart.contains(item) =>
      log.debug(s"Removing $item from cart.")
      context become nonEmpty(cart.removeItem(item), scheduleTimer)
    case StartCheckout =>
      timer.cancel()
      log.debug("Starting checkout")
      context become inCheckout(cart)
    case ExpireCart =>
      log.debug("Timer expired!")
      context become empty
    case other =>
      log.warning(s"Unknown message received: $other.")
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      log.debug("Checkout cancelled.")
      context become nonEmpty(cart, scheduleTimer)
    case ConfirmCheckoutClosed =>
      log.debug("Checkout closed.")
      context become empty
    case other =>
      log.warning(s"Unknown message received: $other.")
  }
}
