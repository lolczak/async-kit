package org.lolczak.async

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}

import org.lolczak.async.error.ThrowableMapper

import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scalaz._
import scalaz.concurrent.{Strategy, Task}
import scalaz.syntax.{ApplicativeOps, BindOps, MonadOps}

object AsyncAction extends AsyncActionFunctions with ToAsyncActionOps with AsyncActionInstances {

}

trait AsyncActionFunctions {

  def async[A](register: ((Throwable \/ A) => Unit) => Unit): AsyncAction[Throwable, A] = liftE(Task.async(attachErrorHandling(register)).attempt)

  def delay[A](a: => A): AsyncAction[Throwable, A] = lift(Task.delay(a))

  def defer[E, A](a: => A)(implicit throwableMapper: ThrowableMapper[E]): AsyncAction[E, A] = delay(a) leftMap throwableMapper

  def fork[A](task: => A)(implicit pool: ExecutorService = Strategy.DefaultExecutorService): AsyncAction[Throwable, A] =
    lift(Task { task })

  def schedule[A](task: => A)(implicit pool: ScheduledExecutorService = Strategy.DefaultTimeoutScheduler) = new {
    def after(delay: Duration): AsyncAction[Throwable, A] = lift(Task.schedule(task, delay))
  }

  def lift[A](task: Task[A]): AsyncAction[Throwable, A] = EitherT.eitherT(task.attempt)

  def liftE[B, A](task: Task[B \/ A]): AsyncAction[B, A] = EitherT.eitherT(task)

  def return_[A](value: => A)(implicit MT: MonadTrans[EitherT[?[_], Nothing, ?]]): AsyncAction[Nothing, A] = MT.liftM(Task.delay(value))

  def returnOpt[A](value: => A)(implicit MT: MonadTrans[EitherT[?[_], Nothing, ?]]): AsyncAction[Nothing, Option[A]] = MT.liftM(Task.delay(Option(value)))

  def returnOptFromTryCatch[A](value: => A): AsyncAction[Throwable, Option[A]] = EitherT.eitherT(Task.delay(Option(value)).attempt)

  def returnFromTryCatch[A](value: => A): AsyncAction[Throwable, A] = EitherT.eitherT(Task.delay(value).attempt)

  def raiseError[E, A](e: E)(implicit ME: MonadError[EitherT[Task, E, ?], E]): AsyncAction[E, A] = ME.raiseError(e)

  def runSync[Error, Success](action: AsyncAction[Error, Success]): Error \/ Success = action.run.unsafePerformSync

  def runAsync[Error, Success](action: AsyncAction[Error, Success])(register: (Error \/ Success) => Unit): Unit =
    action.run.unsafePerformAsync {
      case -\/(th) => throw th
      case \/-(result) => register(result)
    }

}

trait ToAsyncActionOps {

  implicit def toActionBindOps[E, A](action: AsyncAction[E, A]): BindOps[AsyncAction[E, ?], A] =
    AsyncAction.asyncActionMonad[E].bindSyntax.ToBindOps[A](action)

  implicit def toActionMonadOps[E, A](action: AsyncAction[E, A]): MonadOps[AsyncAction[E, ?], A] =
    AsyncAction.asyncActionMonad[E].monadSyntax.ToMonadOps[A](action)

  implicit def toActionApplicativeOps[E, A](action: AsyncAction[E, A]): ApplicativeOps[AsyncAction[E, ?], A] =
    AsyncAction.asyncActionMonad[E].applicativeSyntax.ToApplicativeOps[A](action)

  implicit def toActionUpperBounds[E1, E2 >: E1, A1, A2 >: A1](action: AsyncAction[E1, A1]): AsyncAction[E2, A2] = action.asInstanceOf[AsyncAction[E2, A2]]

  implicit def toActionMt[A](value: => A) = new {
    def asAsyncAction[E]: AsyncAction[E, A] = AsyncAction.return_(value)
  }

  implicit class ToActionRecoveryOps[E1, A](action: AsyncAction[E1, A]) {

    val ME = AsyncAction.asyncActionMonadError[E1]
    import ME.monadErrorSyntax._

    def mapError[E2](handler: E1 => E2): AsyncAction[E2, A] = action leftMap handler

    def fallbackTo[B >: A](failover: AsyncAction[E1, B]): AsyncAction[E1, B] = {
      action recoverWith {
        case firstFailure => failover recoverWith { case secondFailure => ME.raiseError(firstFailure) }
      }
    }

    def recover[B >: A](pf: PartialFunction[E1, B]): AsyncAction[E1, B] = {
      action.asInstanceOf[AsyncAction[E1, B]] handleError { err =>
        if (pf.isDefinedAt(err)) AsyncAction.return_(pf(err))
        else AsyncAction.raiseError(err)
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
