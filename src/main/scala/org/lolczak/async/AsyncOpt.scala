package org.lolczak.async

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}

import org.lolczak.async.error._

import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scalaz._
import scalaz.concurrent.{Strategy, Task}
import scalaz.syntax.{ApplicativeOps, MonadOps, BindOps}
import scala.util.{Failure => TryFailure, Success => TrySuccess}

object AsyncOpt extends AsyncOptFunctions with ToAsyncOptOps with AsyncOptInstances {

}

trait AsyncOptFunctions {

  def asyncF[A](start: () => Future[A])(implicit ec: ExecutionContext): AsyncOpt[Throwable, A] =
    async[A] { k =>
      start().onComplete {
        case TryFailure(th)     => k(-\/(th))
        case TrySuccess(result) => k(\/-(result))
      }
    }

  def async[A](register: ((Throwable \/ A) => Unit) => Unit): AsyncOpt[Throwable, A] = liftE(Task.async(attachErrorHandling(register)).attempt)

  def asyncOptF[A](start: () => Future[Option[A]])(implicit ec: ExecutionContext): AsyncOpt[Throwable, A] =
    asyncOpt[A] { k =>
      start().onComplete {
        case TryFailure(th)     => k(-\/(th))
        case TrySuccess(result) => k(\/-(result))
      }
    }

  def asyncOpt[A](register: ((Throwable \/ Option[A]) => Unit) => Unit): AsyncOpt[Throwable, A] =
    OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task.async(attachErrorHandling(register)).attempt))

  def delay[A](a: => A): AsyncOpt[Throwable, A] = lift(Task.delay(a))

  def defer[E, A](a: => A)(implicit throwableMapper: ThrowableMapper[E]): AsyncOpt[E, A] = OptionT[EitherT[Task, E, ?], A](delay(a).run leftMap throwableMapper.mapThrowable)

  def fork[A](task: => Option[A])(implicit pool: ExecutorService = Strategy.DefaultExecutorService): AsyncOpt[Throwable, A] =
    OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task { task } attempt))

  def schedule[A](task: => Option[A])(implicit pool: ScheduledExecutorService = Strategy.DefaultTimeoutScheduler) = new {
    def after(delay: Duration): AsyncOpt[Throwable, A] = OptionT[EitherT[Task, Throwable, ?], A](EitherT.eitherT(Task.schedule(task, delay).attempt))
  }

  def lift[A](task: Task[A]): AsyncOpt[Throwable, A] =
    AsyncOpt.asyncOptActionMonadTrans.liftM[EitherT[Task, Throwable, ?], A](EitherT.eitherT(task.attempt))

  def liftE[B, A](task: Task[B \/ A]): AsyncOpt[B, A] =
    AsyncOpt.asyncOptActionMonadTrans.liftM[EitherT[Task, B, ?], A](EitherT.eitherT(task))

  def return_[A](value: => A): AsyncOpt[Nothing, A] = returnSome[A](value)

  def returnSome[A](value: => A): AsyncOpt[Nothing, A] = OptionT.some[EitherT[Task, Nothing, ?], A](value)

  def returnNone[A]: AsyncOpt[Nothing, A] = OptionT.none[EitherT[Task, Nothing, ?], A]

  def returnFromTryCatch[A](value: => A): AsyncOpt[Throwable, A] = liftE(Task.delay(value).attempt)

  def raiseError[E, A](e: E): AsyncOpt[E, A] = OptionT.optionTMonadError[EitherT[Task, E, ?], E].raiseError(e)

  def runSync[Error, Success](action: AsyncOpt[Error, Success]): Error \/ Option[Success] = action.run.run.unsafePerformSync

  def runAsync[Error, Success](action: AsyncOpt[Error, Success])(register: (Error \/ Option[Success]) => Unit): Unit =
    action.run.run.unsafePerformAsync {
      case -\/(th) => throw th
      case \/-(result) => register(result)
    }

}

trait ToAsyncOptOps {

  implicit def toOptActionBindOps[E, A](action: AsyncOpt[E, A]): BindOps[AsyncOpt[E, ?], A] =
    AsyncOpt.asyncOptActionMonad[E].bindSyntax.ToBindOps[A](action)

  implicit def toOptActionMonadOps[E, A](action: AsyncOpt[E, A]): MonadOps[AsyncOpt[E, ?], A] =
    AsyncOpt.asyncOptActionMonad[E].monadSyntax.ToMonadOps[A](action)

  implicit def toOptActionApplicativeOps[E, A](action: AsyncOpt[E, A]): ApplicativeOps[AsyncOpt[E, ?], A] =
    AsyncOpt.asyncOptActionMonad[E].applicativeSyntax.ToApplicativeOps[A](action)

  implicit def toOptActionUpperBound[E1, E2 >: E1, A1, A2 >: A1](action: AsyncOpt[E1, A1]): AsyncOpt[E2, A2] = action.asInstanceOf[AsyncOpt[E2, A2]]

  implicit def toOptActionMt[A](value: => A) = new {
    def asAsyncOptAction[E]: AsyncOpt[E, A] = AsyncOpt.returnSome(value)
  }

  implicit class ToOptActionRecoveryOps[E1, A](action: AsyncOpt[E1, A]) {

    val ME = AsyncOpt.asyncOptActionMonadError[E1]
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

  implicit class ToOptActionOps[E, A](action: AsyncOpt[E, A]) {

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

trait AsyncOptInstances {

  implicit def asyncOptActionMonad[E] = OptionT.optionTMonadPlus[EitherT[Task, E, ?]]

  implicit val asyncOptActionMonadTrans: MonadTrans[OptionT] = OptionT.optionTMonadTrans

  implicit def asyncOptActionMonadError[E] = OptionT.optionTMonadError[EitherT[Task, E, ?], E]

}