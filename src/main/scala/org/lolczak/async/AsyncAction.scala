package org.lolczak.async

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}

import scala.concurrent.duration.Duration
import scalaz._
import scalaz.concurrent.{Strategy, Task}

object AsyncAction extends ToAsyncActionOps with AsyncActionInstances {

  def functionsFor[E] = new AsyncActionFunctions[E]

  def runSync[Error, Success](action: AsyncAction[Error, Success]): Error \/ Success = action.run.unsafePerformSync

  def runAsync[Error, Success](action: AsyncAction[Error, Success])(register: (Error \/ Success) => Unit): Unit =
    action.run.unsafePerformAsync {
      case -\/(th) => throw th
      case \/-(result) => register(result)
    }

}

class AsyncActionFunctions[E] {

  val MT = implicitly[MonadTrans[EitherT[?[_], E, ?]]]

  val ME = implicitly[MonadError[EitherT[Task, E, ?], E]]

  def async[A](register: ((Throwable \/ A) => Unit) => Unit): AsyncAction[Throwable, A] = liftE(Task.async(register).attempt)

  def delay[A](a: => A): AsyncAction[Throwable, A] = lift(Task.delay(a))

  def fork[A](task: => A)(implicit pool: ExecutorService = Strategy.DefaultExecutorService): AsyncAction[Throwable, A] =
    lift(Task { task })

  def schedule[A](task: => A)(implicit pool: ScheduledExecutorService = Strategy.DefaultTimeoutScheduler) = new {
    def after(delay: Duration): AsyncAction[Throwable, A] = lift(Task.schedule(task, delay))
  }

  def lift[A](task: Task[A]): AsyncAction[Throwable, A] = EitherT.eitherT(task.attempt)

  def liftE[B, A](task: Task[B \/ A]): AsyncAction[B, A] = EitherT.eitherT(task)

  def return_[A](value: => A): AsyncAction[E, A] = MT.liftM(Task.delay(value))

  def returnOpt[A](value: => A): AsyncAction[E, Option[A]] = MT.liftM(Task.delay(Option(value)))

  def returnOptFromTryCatch[A](value: => A): AsyncAction[Throwable, Option[A]] = EitherT.eitherT(Task.delay(Option(value)).attempt)

  def returnFromTryCatch[A](value: => A): AsyncAction[Throwable, A] = EitherT.eitherT(Task.delay(value).attempt)

  def raiseError[A](e: E): AsyncAction[E, A] = ME.raiseError(e)

  implicit def toMt[A](value: => A) = new {
    def asAsyncAction: AsyncAction[E, A] = return_(value)
  }

}

trait ToAsyncActionOps {

  implicit class ErrorHandler[E1, A](action: AsyncAction[E1, A]) {

    val fun = new AsyncActionFunctions[E1]
    import fun.ME.monadErrorSyntax._
    import fun._

    def mapError[E2](handler: E1 => E2): AsyncAction[E2, A] = action leftMap handler

    def fallbackTo[B >: A](failover: AsyncAction[E1, B]): AsyncAction[E1, B] = {
      action recoverWith {
        case firstFailure => failover recoverWith { case secondFailure => ME.raiseError(firstFailure) }
      }
    }

    def recover[B >: A](pf: PartialFunction[E1, B]): AsyncAction[E1, B] = {
      action.asInstanceOf[AsyncAction[E1, B]] handleError { err =>
        if (pf.isDefinedAt(err)) return_(pf(err))
        else raiseError(err)
      }
    }

    def recoverWith[B >: A](pf: PartialFunction[E1, AsyncAction[E1, B]]): AsyncAction[E1, B] = {
      action.asInstanceOf[AsyncAction[E1, B]] handleError { err =>
        if (pf.isDefinedAt(err)) pf(err)
        else ME.raiseError(err)
      }
    }
  }

  implicit class ToActionOps[E, A](action: AsyncAction[E, A]) {

    def executeSync(): E \/ A = AsyncAction.runSync(action)

    def executeAsync(register: (E \/ A) => Unit): Unit = AsyncAction.runAsync(action)(register)

  }

}

trait AsyncActionInstances {

  implicit def asyncActionMonad[E] = implicitly[Monad[AsyncAction[E, ?]]]

  implicit def asyncActionMonadError[E] = implicitly[MonadError[AsyncAction[E, ?], E]]

  implicit def asyncActionMonadTrans[E] = implicitly[MonadTrans[EitherT[?[_], E, ?]]]

}
