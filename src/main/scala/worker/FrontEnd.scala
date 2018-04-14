package worker

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, Cancellable, Props, Timers}
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.duration._

/**
 * Dummy front-end that periodically sends a workload to the master.
 */
object FrontEnd {

  def props: Props = Props(new FrontEnd)

  private case object NotOk
  private case object Tick
  private case object Retry
}

// #front-end
class FrontEnd extends Actor with ActorLogging with Timers {
  import FrontEnd._
  import context.dispatcher

  val masterProxy = context.actorOf(
    MasterSingleton.proxyProps(context.system),
    name = "masterProxy")

  var workCounter = 0

  def nextWorkId(): String = UUID.randomUUID().toString

  // send a Tick msg to itself as FrontEnd is an Actor.
  // the timer is started in an overrided method preStrat() only once and then disappear
  override def preStart(): Unit = {
    timers.startSingleTimer("tick", Tick, 5.seconds)
  }

  def receive = idle

  def idle: Receive = {
    case Tick =>
      workCounter += 1
      log.info("Produced work: {}", workCounter)
      val work = Work(nextWorkId(), workCounter)
      // context only valid within the current actor
      // context.become() let the actor switch to a different Receive(msg handler)
      context.become(busy(work))
  }

  def busy(workInProgress: Work): Receive = {
    sendWork(workInProgress)

    {
      case Master.Ack(workId) =>
        log.info("Got ack for workId {}", workId)
        val nextTick = ThreadLocalRandom.current.nextInt(3, 10).seconds
        // schedule a Tick to itself within a duration bewteen 3 to 10 seconds
        // swicth status to idle right after that -> ready to produce another work
        timers.startSingleTimer(s"tick", Tick, nextTick)
        context.become(idle)

      case NotOk =>
        log.info("Work {} not accepted, retry after a while", workInProgress.workId)
        timers.startSingleTimer("retry", Retry, 3.seconds)

      case Retry =>
        log.info("Retrying work {}", workInProgress.workId)
        sendWork(workInProgress)
    }
  }

  def sendWork(work: Work): Unit = {
    // ? : Akka ask pattern requires an implicit Timeout and returns a Future[Any]
    // Future[].recover will either handle any type of throwables with { inline function } or skip over it when there is no exceptions
    implicit val timeout = Timeout(5.seconds)
    (masterProxy ? work).recover {
      case _ => NotOk
    } pipeTo self
  }

}
// #front-end