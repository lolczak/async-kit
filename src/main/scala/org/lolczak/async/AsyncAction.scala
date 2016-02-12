package org.lolczak.async

import scalaz._
import scalaz.concurrent.Task

object AsyncAction extends AsyncActionFunctions with ToAsyncActionOps with AsyncActionInstances {

  def apply[E, A](f: ErrorCarrier[E] => AsyncAction[E, A]): AsyncAction[E, A] = f(ErrorCarrier[E]())

}

trait AsyncActionFunctions {


  //  implicit val M = implicitly[Monad[({type λ[A] = AsyncAction[E, A]})#λ]]

  def return_[E, A](value: => A)(implicit ev: ErrorCarrier[E]): AsyncAction[E, A] = lift(Task.delay(value))

  def returnOpt[E, A](value: => A)(implicit ev: ErrorCarrier[E]): AsyncAction[E, Option[A]] = lift(Task.delay(Option(value)))

  def lift[A, E](task: Task[A])(implicit ev: ErrorCarrier[E], MT: MonadTrans[({type λ[F[_], B] = EitherT[F, E, B]})#λ]): AsyncAction[E, A] = MT.liftM(task)

  def returnOptFromTryCatch[A](value: => A): AsyncAction[Throwable, Option[A]] = EitherT.eitherT(Task.delay(Option(value)).attempt)

  def returnFromTryCatch[A](value: => A): AsyncAction[Throwable, A] = EitherT.eitherT(Task.delay(value).attempt)

  def raiseError[E, A, C](e: C)(implicit ev1: ErrorCarrier[E], ev2: C <:< E, ME: MonadError[({type λ[A] = EitherT[Task, E, A]})#λ, E]): AsyncAction[E, A] = ME.raiseError(e)

  def runSync[Error, Success](action: AsyncAction[Error, Success]): Error \/ Success = action.run.unsafePerformSync

  def runAsync[Error, Success](action: AsyncAction[Error, Success])(register: (Error \/ Success) => Unit): Unit =
    action.run.unsafePerformAsync {
      case -\/(th) => throw th
      case \/-(result) => register(result)
    }

  def async[A](register: ((Throwable \/ A) => Unit) => Unit): AsyncAction[Throwable, A] = liftE(Task.async(register).attempt)

  def liftE[B, A](task: Task[B \/ A]): AsyncAction[B, A] = EitherT.eitherT(task)

}

trait ToAsyncActionOps {

  implicit def toMt[A, E](a: => A)(implicit ev: ErrorCarrier[E]): AsyncAction[E, A] = AsyncAction.return_(a)

  implicit class ErrorHandler[B, A](action: AsyncAction[B, A]) {

    def mapError[E, C](handler: B => C)(implicit ev1: ErrorCarrier[E], ev2: C <:< E): AsyncAction[E, A] = action leftMap (handler andThen (_.asInstanceOf[E]))

    def recoverWith(pf: PartialFunction[B, AsyncAction[B, A]]): AsyncAction[B, A] = {
      val ME = implicitly[MonadError[({type λ[X] = EitherT[Task, B, X]})#λ, B]]
      import ME.monadErrorSyntax._

      action handleError { err =>
        if (pf.isDefinedAt(err)) pf(err)
        else action
      }
    }
  }

  implicit class ToActionOps[A, E](action: AsyncAction[E, A]) {

    def executeSync(): E \/ A = AsyncAction.runSync(action)

    def executeAsync(register: (E \/ A) => Unit): Unit = AsyncAction.runAsync(action)(register)

  }

}

trait AsyncActionInstances {

  implicit def asyncActionMonad[E] = implicitly[Monad[({type λ[A] = AsyncAction[E, A]})#λ]]

}
