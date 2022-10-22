package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.Payment

object TypedCartActor {

  sealed trait Command

  case class AddItem(item: Any) extends Command

  case class RemoveItem(item: Any) extends Command

  case object ExpireCart extends Command

  case class StartCheckout(orderManagerCartRef: ActorRef[TypedCartActor.Event]) extends Command

  case object ConfirmCheckoutCancelled extends Command

  case object ConfirmCheckoutClosed extends Command

  case class GetItems(sender: ActorRef[Cart]) extends Command // command made to make testing easier

  sealed trait Event

  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
}

class TypedCartActor(cartAdapter: ActorRef[TypedCartActor.Event] = null,
                     checkoutAdapter: ActorRef[TypedCheckout.Event] = null,
                     paymentAdapter: ActorRef[Payment.Event] = null
                    ) {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable = context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case AddItem(item) =>
          context.log.debug(s"Adding $item to the cart.")
          nonEmpty(Cart.empty.addItem(item), scheduleTimer(context))
        case GetItems(sender) =>
          context.log.debug(s"Showing items in cart.")
          sender ! Cart.empty
          Behaviors.same
        case other =>
          context.log.warn(s"Unknown message received: $other.")
          Behaviors.same
      }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case AddItem(item) =>
          context.log.debug(s"Adding $item to the cart.")
          nonEmpty(cart.addItem(item), scheduleTimer(context))
        case RemoveItem(item) if cart.contains(item) && cart.items.size == 1 =>
          timer.cancel()
          context.log.debug(s"Removing $item from cart.")
          empty
        case RemoveItem(item) if cart.contains(item) =>
          context.log.debug(s"Removing $item from cart.")
          nonEmpty(cart.removeItem(item), scheduleTimer(context))
        case ExpireCart =>
          context.log.debug("Timer expired!")
          empty
        case StartCheckout(orderManagerCartRef: ActorRef[TypedCartActor.Event]) =>
          timer.cancel()
          context.log.debug("Starting checkout...")
          val checkout = context.spawn(new TypedCheckout(context.self, cartAdapter, checkoutAdapter, paymentAdapter).start, "checkout")
          checkout ! TypedCheckout.StartCheckout
          orderManagerCartRef ! CheckoutStarted(checkout)
          inCheckout(cart)
        case GetItems(sender) =>
          context.log.debug(s"Showing items in cart.")
          sender ! cart
          Behaviors.same
        case other =>
          context.log.warn(s"Unknown message received: $other.")
          Behaviors.same
      }
  )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case ConfirmCheckoutCancelled =>
          context.log.debug("Checkout cancelled.")
          nonEmpty(cart, scheduleTimer(context))
        case ConfirmCheckoutClosed =>
          context.log.debug("Checkout closed.")
          empty
        case GetItems(sender) =>
          context.log.debug(s"Showing items in cart.")
          sender ! cart
          Behaviors.same
        case other =>
          context.log.warn(s"Unknown message received: $other.")
          Behaviors.same
      }
  )
}
