package org.lolczak.async

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}

import org.lolczak.async.error._

import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scalaz._
import scalaz.concurrent.{Strategy, Task}
import scalaz.syntax.{ApplyOps, ApplicativeOps, MonadOps, BindOps}
import scala.util.{Failure => TryFailure, Success => TrySuccess}

object AsyncOpt extends AsyncOptFunctions with AsyncOptInstances {

}

trait AsyncOptFunctions extends ToAsyncOptOps {

  def spawn[A](start: () => Future[A])(implicit ec: ExecutionContext): AsyncOpt[Exception, A] =
    async[A] { k =>
      start().onComplete {
        case TryFailure(th)     => k(-\/(th))
        case TrySuccess(result) => k(\/-(result))
      }
    }

  def async[A](register: ((Throwable \/ A) => Unit) => Unit): AsyncOpt[Exception, A] = liftE(Task.async(attachErrorHandling(register)).attempt) mapError ThrowableHandler

  def spawnOpt[A](start: () => Future[Option[A]])(implicit ec: ExecutionContext): AsyncOpt[Exception, A] =
    asyncOpt[A] { k =>
      start().onComplete {
        case TryFailure(th)     => k(-\/(th))
        case TrySuccess(result) => k(\/-(result))
      }
    }

  def asyncOpt[A](register: ((Throwable \/ Option[A]) => Unit) => Unit): AsyncOpt[Exception, A] =
    OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task.async(attachErrorHandling(register)).attempt)) mapError ThrowableHandler

  def delay[A](a: => A): AsyncOpt[Exception, A] = lift(Task.delay(a))

  def defer[E, A](a: => A)(implicit throwableMapper: ThrowableMapper[E]): AsyncOpt[E, A] = OptionT[EitherT[Task, E, ?], A](delay(a).run leftMap throwableMapper.mapThrowable)

  def fork[A](task: => Option[A])(implicit pool: ExecutorService = Strategy.DefaultExecutorService): AsyncOpt[Exception, A] =
    OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task { task } attempt)) mapError ThrowableHandler

  def schedule[A](task: => Option[A])(implicit pool: ScheduledExecutorService = Strategy.DefaultTimeoutScheduler) = new {
    def after(delay: Duration): AsyncOpt[Exception, A] = OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task.schedule(task, delay).attempt)) mapError ThrowableHandler
  }

  def lift[A](task: Task[A]): AsyncOpt[Exception, A] =
    AsyncOpt.asyncOptMonadTrans.liftM[EitherT[Task, Throwable, ?], A](EitherT.eitherT(task.attempt)) mapError ThrowableHandler

  def liftE[B, A](task: Task[B \/ A]): AsyncOpt[B, A] =
    AsyncOpt.asyncOptMonadTrans.liftM[EitherT[Task, B, ?], A](EitherT.eitherT(task))

  def return_[A](value: => A): AsyncOpt[Nothing, A] = returnSome[A](value)

  def returnSome[A](value: => A): AsyncOpt[Nothing, A] = OptionT.some[EitherT[Task, Nothing, ?], A](value)

  def returnNone[A]: AsyncOpt[Nothing, A] = OptionT.none[EitherT[Task, Nothing, ?], A]

  def returnFromTryCatch[A](value: => A): AsyncOpt[Exception, A] = liftE(Task.delay(value).attempt) mapError ThrowableHandler

  def raiseError[E, A](e: E): AsyncOpt[E, A] = OptionT.optionTMonadError[EitherT[Task, E, ?], E].raiseError(e)

  def runSync[Error, Success](action: AsyncOpt[Error, Success]): Error \/ Option[Success] = action.run.run.unsafePerformSync

  def runAsync[Error, Success](action: AsyncOpt[Error, Success])(register: (Error \/ Option[Success]) => Unit): Unit =
    action.run.run.unsafePerformAsync {
      case -\/(th) => throw th
      case \/-(result) => register(result)
    }

}

trait ToAsyncOptOps {

  implicit def toOptBindOps[E, A](action: AsyncOpt[E, A]): BindOps[AsyncOpt[E, ?], A] =
    AsyncOpt.asyncOptMonad[E].bindSyntax.ToBindOps[A](action)

  implicit def toOptApplyOps[E, A](action: AsyncOpt[E, A]): ApplyOps[AsyncOpt[E, ?], A] =
    AsyncOpt.asyncOptMonad[E].applySyntax.ToApplyOps[A](action)

  implicit def toOptMonadOps[E, A](action: AsyncOpt[E, A]): MonadOps[AsyncOpt[E, ?], A] =
    AsyncOpt.asyncOptMonad[E].monadSyntax.ToMonadOps[A](action)

  implicit def toOptApplicativeOps[E, A](action: AsyncOpt[E, A]): ApplicativeOps[AsyncOpt[E, ?], A] =
    AsyncOpt.asyncOptMonad[E].applicativeSyntax.ToApplicativeOps[A](action)

  implicit def toOptUpperBound[E1, E2 >: E1, A1, A2 >: A1](action: AsyncOpt[E1, A1]): AsyncOpt[E2, A2] = action.asInstanceOf[AsyncOpt[E2, A2]]

  implicit def toOptMt[A](value: => A) = new {
    def asAsyncOpt[E]: AsyncOpt[E, A] = AsyncOpt.returnSome(value)
  }

  implicit class ToOptRecoveryOps[E1, A](action: AsyncOpt[E1, A]) {

    val ME = AsyncOpt.asyncOptMonadError[E1]
    import ME.monadErrorSyntax._

    def mapError[E2](handler: E1 => E2): AsyncOpt[E2, A] = OptionT[EitherT[Task, E2, ?], A](action.run leftMap handler)

    def recover[B >: A](pf: PartialFunction[E1, B]): AsyncOpt[E1, B] = {
      action.asInstanceOf[AsyncOpt[E1, B]] handleError { err =>
        if (pf.isDefinedAt(err)) AsyncOpt.return_(pf(err))
        else AsyncOpt.raiseError(err)
      }
    }

    def recoverWith[B >: A](pf: PartialFunction[E1, AsyncOpt[E1, B]]): AsyncOpt[E1, B] = {
      action.asInstanceOf[AsyncOpt[E1, B]] handleError { err =>
        if (pf.isDefinedAt(err)) pf(err)
        else ME.raiseError(err)
      }
    }

  }

  implicit class ToAsyncOptExecOps[E, A](action: AsyncOpt[E, A]) {

    def execute(): Future[E \/ Option[A]] = {
      val promise = Promise[E \/ Option[A]]()
      action.run.run.unsafePerformAsync {
        case -\/(th)     => promise.failure(th)
        case \/-(result) => promise.success(result)
      }
      promise.future
    }

    def executeSync(): E \/ Option[A] = AsyncOpt.runSync(action)

    def executeAsync(register: (E \/ Option[A]) => Unit): Unit = AsyncOpt.runAsync(action)(register)

  }

}

trait AsyncOptInstances extends LowPrioImplicitInstances {

  implicit def asyncOptMonad[E] = OptionT.optionTMonadPlus[EitherT[Task, E, ?]]

  implicit val asyncOptMonadTrans: MonadTrans[OptionT] = OptionT.optionTMonadTrans

}

trait LowPrioImplicitInstances {

  implicit def asyncOptMonadError[E] = OptionT.optionTMonadError[EitherT[Task, E, ?], E]

}