package org.lolczak.async.akka.pattern

import _root_.akka.actor.Actor
import _root_.akka.actor.ActorRef
import _root_.akka.actor.Status
import org.lolczak.async.Async

import scala.concurrent.ExecutionContext
import scalaz.{-\/, \/-}
import scalaz.concurrent.Task

trait PipeSupport {

  final class PipeableTask[T](val task: Task[T])(implicit executionContext: ExecutionContext) {
    def pipeTo(recipient: ActorRef)(implicit sender: ActorRef = Actor.noSender): Unit = {
      task.unsafePerformAsync {
        case \/-(r) => recipient ! r
        case -\/(f) => recipient ! Status.Failure(f)
      }
    }
  }

  final class PipeableAction[E, T](val action: Async[E, T])(implicit executionContext: ExecutionContext) {
    def pipeTo(recipient: ActorRef)(implicit sender: ActorRef = Actor.noSender): Unit = {
      action.run.unsafePerformAsync {
        case -\/(f) => recipient ! Status.Failure(f)
        case \/-(r) => recipient ! r
      }
    }
  }

  implicit def pipe[T](future: Task[T])(implicit executionContext: ExecutionContext): PipeableTask[T] = new PipeableTask(future)

  implicit def pipe[E, T](action: Async[E, T])(implicit executionContext: ExecutionContext): PipeableAction[E, T] = new PipeableAction[E, T](action)

}
