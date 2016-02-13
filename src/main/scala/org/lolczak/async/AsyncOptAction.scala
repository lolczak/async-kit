package org.lolczak.async

import java.util.concurrent.{ScheduledExecutorService, ExecutorService}

import scala.concurrent.duration.Duration
import scalaz.concurrent.{Strategy, Task}
import scalaz._

object AsyncOptAction extends ToAsyncOptActionOps {

  def functionsFor[E] = new AsyncOptActionFunctions[E]

  def runSync[Error, Success](action: AsyncOptAction[Error, Success]): Error \/ Option[Success] = action.run.run.unsafePerformSync

  def runAsync[Error, Success](action: AsyncOptAction[Error, Success])(register: (Error \/ Option[Success]) => Unit): Unit =
    action.run.run.unsafePerformAsync {
      case -\/(th)     => throw th
      case \/-(result) => register(result)
    }
}

class AsyncOptActionFunctions[E] {

  val MT = implicitly[MonadTrans[OptionT]]

  val ME = OptionT.optionTMonadError[EitherT[Task, E, ?], E]//implicitly[MonadError[OptionT[EitherT[Task, E, ?], ?], E]]

  def async[A](register: ((Throwable \/ A) => Unit) => Unit): AsyncOptAction[Throwable, A] = liftE(Task.async(register).attempt)

  def asyncOpt[A](register: ((Throwable \/ Option[A]) => Unit) => Unit): AsyncOptAction[Throwable, A] =
    OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task.async(register).attempt))

  def fork[A](task: =>Option[A])(implicit pool: ExecutorService = Strategy.DefaultExecutorService): AsyncOptAction[Throwable, A] =
    OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task { task } attempt))

  def schedule[A](task: =>Option[A])(implicit pool: ScheduledExecutorService = Strategy.DefaultTimeoutScheduler) = new {
    def after(delay: Duration): AsyncOptAction[Throwable, A] = OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task.schedule(task, delay).attempt))
  }

  def lift[A](task: Task[A]): AsyncOptAction[Throwable, A] = MT.liftM[EitherT[Task, Throwable, ?], A](EitherT.eitherT(task.attempt))

  def liftE[B, A](task: Task[B \/ A]): AsyncOptAction[B, A] = MT.liftM[EitherT[Task, B, ?], A](EitherT.eitherT(task))

  def return_[A](value: =>A): AsyncOptAction[E, A] = returnSome[A](value) //lift(Task.delay(value))

  def returnSome[A](value: =>A): AsyncOptAction[E, A] = OptionT.some[EitherT[Task, E, ?], A](value)

  def returnNone[A]: AsyncOptAction[E, A] = OptionT.none[EitherT[Task, E, ?], A]

  def returnFromTryCatch[A](value: =>A): AsyncOptAction[Throwable, A] = liftE(Task.delay(value).attempt)

  def raiseError[A](e: E): AsyncOptAction[E, A] = ME.raiseError(e)

}

trait ToAsyncOptActionOps {

  implicit class ErrorHandler[E1, A](action: AsyncOptAction[E1, A]) {

    val ME = OptionT.optionTMonadError[EitherT[Task, E1, ?], E1]
    import ME.monadErrorSyntax._

    def mapError[E2](handler: E1 => E2): AsyncOptAction[E2, A] = OptionT[EitherT[Task, E2, ?], A](action.run leftMap handler)

    def recover[B >: A](pf: PartialFunction[E1, B]): AsyncOptAction[E1, B] = {
      val fun = new AsyncOptActionFunctions[E1]
      import fun._
      action.asInstanceOf[AsyncOptAction[E1, B]] handleError { err =>
        if (pf.isDefinedAt(err)) return_(pf(err))
        else raiseError(err)
      }
    }

    def recoverWith[B >: A](pf: PartialFunction[E1, AsyncOptAction[E1, B]]): AsyncOptAction[E1, B] = {
      action.asInstanceOf[AsyncOptAction[E1, B]] handleError { err =>
        if (pf.isDefinedAt(err)) pf(err)
        else ME.raiseError(err)
      }
    }

  }

  implicit class ToOptActionOps[E, A](action: AsyncOptAction[E, A]) {

    def executeSync(): E \/ Option[A] = AsyncOptAction.runSync(action)

    def executeAsync(register: (E \/ Option[A]) => Unit): Unit = AsyncOptAction.runAsync(action)(register)

  }

//  implicit def toMt[A, E](a: =>A): AsyncOptAction[E, A] = AsyncOptAction.return_(a)

}
