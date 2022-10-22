package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.Effect.{Scheduled, Spawned}
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
  extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    //sync
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[Cart]()

    testKit.run(TypedCartActor.AddItem("item1"))
    testKit.run(TypedCartActor.GetItems(inbox.ref))
    inbox.expectMessage(Cart(Seq("item1")))
  }

  it should "be empty after adding and removing the same item" in {
    //async
    val cartActor = testKit.spawn(new TypedCartActor().start)
    val probe = testKit.createTestProbe[Any]()

    cartActor ! AddItem("item2")
    cartActor ! RemoveItem("item2")
    cartActor ! GetItems(probe.ref)
    probe.expectMessage(Cart.empty)
  }

  it should "start checkout" in {
    //sync
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[TypedCartActor.Event]()

    testKit.run(AddItem("item3"))
    testKit.run(StartCheckout(inbox.ref))

    testKit.expectEffectType[Scheduled[TypedCartActor]]
    testKit.expectEffectType[Spawned[TypedCheckout]]

    val childInbox = testKit.childInbox[TypedCheckout.Command]("checkout")
    childInbox.expectMessage(TypedCheckout.StartCheckout)
    inbox.expectMessage(_: TypedCartActor.CheckoutStarted)
  }
}
