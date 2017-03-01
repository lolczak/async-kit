package org.lolczak.async

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}

import org.lolczak.async.error.ThrowableMapper

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.implicitConversions
import scala.util.{Failure => TryFailure, Success => TrySuccess}
import scalaz._
import scalaz.concurrent.{Strategy, Task}
import scalaz.syntax.{ApplicativeOps, BindOps, MonadOps}

object Async extends AsyncFunctions with ToAsyncOps with AsyncInstances {

}

trait AsyncFunctions {

  def asyncF[A](start: () => Future[A])(implicit ec: ExecutionContext): Async[Throwable, A] =
    async[A] { k =>
      start().onComplete {
        case TryFailure(th)     => k(-\/(th))
        case TrySuccess(result) => k(\/-(result))
      }
    }

  def async[A](register: ((Throwable \/ A) => Unit) => Unit): Async[Throwable, A] = liftE(Task.async(attachErrorHandling(register)).attempt)

  def delay[A](a: => A): Async[Throwable, A] = lift(Task.delay(a))

  def defer[E, A](a: => A)(implicit throwableMapper: ThrowableMapper[E]): Async[E, A] = delay(a) leftMap throwableMapper.mapThrowable

  def fork[A](task: => A)(implicit pool: ExecutorService = Strategy.DefaultExecutorService): Async[Throwable, A] =
    lift(Task { task })

  def schedule[A](task: => A)(implicit pool: ScheduledExecutorService = Strategy.DefaultTimeoutScheduler) = new {
    def after(delay: Duration): Async[Throwable, A] = lift(Task.schedule(task, delay))
  }

  def lift[A](task: Task[A]): Async[Throwable, A] = EitherT.eitherT(task.attempt)

  def liftE[B, A](task: Task[B \/ A]): Async[B, A] = EitherT.eitherT(task)

  def return_[A](value: => A)(implicit MT: MonadTrans[EitherT[?[_], Nothing, ?]]): Async[Nothing, A] = MT.liftM(Task.delay(value))

  def returnOpt[A](value: => A)(implicit MT: MonadTrans[EitherT[?[_], Nothing, ?]]): Async[Nothing, Option[A]] = MT.liftM(Task.delay(Option(value)))

  def returnOptFromTryCatch[A](value: => A): Async[Throwable, Option[A]] = EitherT.eitherT(Task.delay(Option(value)).attempt)

  def returnFromTryCatch[A](value: => A): Async[Throwable, A] = EitherT.eitherT(Task.delay(value).attempt)

  def raiseError[E, A](e: E)(implicit ME: MonadError[EitherT[Task, E, ?], E]): Async[E, A] = ME.raiseError(e)

  def runSync[Error, Success](action: Async[Error, Success]): Error \/ Success = action.run.unsafePerformSync

  def runAsync[Error, Success](action: Async[Error, Success])(register: (Error \/ Success) => Unit): Unit =
    action.run.unsafePerformAsync {
      case -\/(th) => throw th
      case \/-(result) => register(result)
    }

}

trait ToAsyncOps {

  implicit def toActionBindOps[E, A](action: Async[E, A]): BindOps[Async[E, ?], A] =
    Async.asyncActionMonad[E].bindSyntax.ToBindOps[A](action)

  implicit def toActionMonadOps[E, A](action: Async[E, A]): MonadOps[Async[E, ?], A] =
    Async.asyncActionMonad[E].monadSyntax.ToMonadOps[A](action)

  implicit def toActionApplicativeOps[E, A](action: Async[E, A]): ApplicativeOps[Async[E, ?], A] =
    Async.asyncActionMonad[E].applicativeSyntax.ToApplicativeOps[A](action)

  implicit def toActionUpperBounds[E1, E2 >: E1, A1, A2 >: A1](action: Async[E1, A1]): Async[E2, A2] = action.asInstanceOf[Async[E2, A2]]

  implicit def toActionMt[A](value: => A) = new {
    def asAsyncAction[E]: Async[E, A] = Async.return_(value)
  }

  implicit class ToActionRecoveryOps[E1, A](action: Async[E1, A]) {

    val ME = Async.asyncActionMonadError[E1]
    import ME.monadErrorSyntax._

    def mapError[E2](handler: E1 => E2): Async[E2, A] = action leftMap handler

    def fallbackTo[B >: A](failover: Async[E1, B]): Async[E1, B] = {
      action recoverWith {
        case firstFailure => failover recoverWith { case secondFailure => ME.raiseError(firstFailure) }
      }
    }

    def recover[B >: A](pf: PartialFunction[E1, B]): Async[E1, B] = {
      action.asInstanceOf[Async[E1, B]] handleError { err =>
        if (pf.isDefinedAt(err)) Async.return_(pf(err))
        else Async.raiseError(err)
      }
    }

    def recoverWith[B >: A](pf: PartialFunction[E1, Async[E1, B]]): Async[E1, B] = {
      action.asInstanceOf[Async[E1, B]] handleError { err =>
        if (pf.isDefinedAt(err)) pf(err)
        else ME.raiseError(err)
      }
    }
  }

  implicit class ToActionOps[E, A](action: Async[E, A]) {

    def execute(): Future[E \/ A] = {
      val promise = Promise[E \/ A]()
      action.run.unsafePerformAsync {
        case -\/(th)     => promise.failure(th)
        case \/-(result) => promise.success(result)
      }
      promise.future
    }

    def executeSync(): E \/ A = Async.runSync(action)

    def executeAsync(register: (E \/ A) => Unit): Unit = Async.runAsync(action)(register)

  }

}

trait AsyncInstances {

  implicit def asyncActionMonad[E] = implicitly[Monad[Async[E, ?]]]

  implicit def asyncActionMonadError[E] = implicitly[MonadError[Async[E, ?], E]]

  implicit def asyncActionMonadTrans[E] = implicitly[MonadTrans[EitherT[?[_], E, ?]]]

}
