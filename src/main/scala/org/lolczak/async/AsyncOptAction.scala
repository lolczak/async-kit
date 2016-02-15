package org.lolczak.async

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}

import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scalaz._
import scalaz.concurrent.{Strategy, Task}

object AsyncOptAction extends AsyncOptActionFunctions with ToAsyncOptActionOps with AsyncOptActionInstances {

}

trait AsyncOptActionFunctions {

  def async[A](register: ((Throwable \/ A) => Unit) => Unit): AsyncOptAction[Throwable, A] = liftE(Task.async(register).attempt)

  def asyncOpt[A](register: ((Throwable \/ Option[A]) => Unit) => Unit): AsyncOptAction[Throwable, A] =
    OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task.async(register).attempt))

  def delay[A](a: => A): AsyncOptAction[Throwable, A] = lift(Task.delay(a))

  def fork[A](task: => Option[A])(implicit pool: ExecutorService = Strategy.DefaultExecutorService): AsyncOptAction[Throwable, A] =
    OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task { task } attempt))

  def schedule[A](task: => Option[A])(implicit pool: ScheduledExecutorService = Strategy.DefaultTimeoutScheduler) = new {
    def after(delay: Duration): AsyncOptAction[Throwable, A] = OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task.schedule(task, delay).attempt))
  }

  def lift[A](task: Task[A]): AsyncOptAction[Throwable, A] =
    AsyncOptAction.asyncOptActionMonadTrans.liftM[EitherT[Task, Throwable, ?], A](EitherT.eitherT(task.attempt))

  def liftE[B, A](task: Task[B \/ A]): AsyncOptAction[B, A] =
    AsyncOptAction.asyncOptActionMonadTrans.liftM[EitherT[Task, B, ?], A](EitherT.eitherT(task))

  def return_[A](value: => A): AsyncOptAction[Nothing, A] = returnSome[A](value)

  def returnSome[A](value: => A): AsyncOptAction[Nothing, A] = OptionT.some[EitherT[Task, Nothing, ?], A](value)

  def returnNone[A]: AsyncOptAction[Nothing, A] = OptionT.none[EitherT[Task, Nothing, ?], A]

  def returnFromTryCatch[A](value: => A): AsyncOptAction[Throwable, A] = liftE(Task.delay(value).attempt)

  def raiseError[E, A](e: E): AsyncOptAction[E, A] = OptionT.optionTMonadError[EitherT[Task, E, ?], E].raiseError(e)

  def runSync[Error, Success](action: AsyncOptAction[Error, Success]): Error \/ Option[Success] = action.run.run.unsafePerformSync

  def runAsync[Error, Success](action: AsyncOptAction[Error, Success])(register: (Error \/ Option[Success]) => Unit): Unit =
    action.run.run.unsafePerformAsync {
      case -\/(th) => throw th
      case \/-(result) => register(result)
    }

}

trait ToAsyncOptActionOps {

  implicit def toUpperBound[E1, E2 >: E1, A1, A2 >: A1](action: AsyncOptAction[E1, A1]): AsyncOptAction[E2, A2] = action.asInstanceOf[AsyncOptAction[E2, A2]]

  implicit def toMt[A](value: => A) = new {
    def asAsyncOptAction[E]: AsyncOptAction[E, A] = AsyncOptAction.returnSome(value)
  }

  implicit class ErrorHandler[E1, A](action: AsyncOptAction[E1, A]) {

    val ME = AsyncOptAction.asyncOptActionMonadError[E1]
    import ME.monadErrorSyntax._

    def mapError[E2](handler: E1 => E2): AsyncOptAction[E2, A] = OptionT[EitherT[Task, E2, ?], A](action.run leftMap handler)

    def recover[B >: A](pf: PartialFunction[E1, B]): AsyncOptAction[E1, B] = {
      action.asInstanceOf[AsyncOptAction[E1, B]] handleError { err =>
        if (pf.isDefinedAt(err)) AsyncOptAction.return_(pf(err))
        else AsyncOptAction.raiseError(err)
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

}

trait AsyncOptActionInstances {

  implicit def asyncOptActionMonad[E] = OptionT.optionTMonadPlus[EitherT[Task, E, ?]]

  implicit val asyncOptActionMonadTrans: MonadTrans[OptionT] = OptionT.optionTMonadTrans

  implicit def asyncOptActionMonadError[E] = OptionT.optionTMonadError[EitherT[Task, E, ?], E]

}