//package org.lolczak.async
//
//import scalaz.concurrent.Task
//import scalaz._
//
//object AsyncOptAction extends AsyncOptActionFunctions with ToAsyncOptActionOps {
//
//  def apply[E, A](f: ErrorCarrier[E] => AsyncOptAction[E, A]): AsyncOptAction[E, A] = f(ErrorCarrier[E]())
//
//}
//
//trait AsyncOptActionFunctions {
//
//  def lift[E, A](task: Task[A])(implicit ev: ErrorCarrier[E], MT: MonadTrans[OptionT]): AsyncOptAction[E, A] = MT.liftM[({type λ[B] = EitherT[Task, E, B]})#λ, A](AsyncAction.lift(task))
//
//  def liftE[E, A](task: Task[E \/ A])(implicit MT: MonadTrans[OptionT]): AsyncOptAction[E, A] = MT.liftM[({type λ[B] = EitherT[Task, E, B]})#λ, A](EitherT.eitherT(task))
//
//  def async[A](register: ((Throwable \/ A) => Unit) => Unit): AsyncOptAction[Throwable, A] = liftE(Task.async(register).attempt)
//
//  def return_[E, A](value: =>A)(implicit ev: ErrorCarrier[E]): AsyncOptAction[E, A] = returnSome[E, A](value) //lift(Task.delay(value))
//
//  def returnSome[E, A](value: =>A)(implicit ev: ErrorCarrier[E]): AsyncOptAction[E, A] = OptionT.some[({type λ[B] = EitherT[Task, E, B]})#λ, A](value)
//
//  def returnNone[E, A](implicit ev: ErrorCarrier[E]): AsyncOptAction[E, A] = OptionT.none[({type λ[B] = EitherT[Task, E, B]})#λ, A]
//
//  def returnFromTryCatch[A](value: =>A): AsyncOptAction[Throwable, A] = liftE(Task.delay(value).attempt)
//
//  def raiseError[E, A, C](e: C)(implicit ev1: ErrorCarrier[E], ev2: C <:< E, ME: MonadError[({type λ[B] = OptionT[({type λ[A1] = EitherT[Task, E, A1]})#λ, B]})#λ, E]): AsyncOptAction[E, A] = ME.raiseError(e)
//
//  def runSync[Error, Success](action: AsyncOptAction[Error, Success]): Error \/ Option[Success] = action.run.run.unsafePerformSync
//
//  def runAsync[Error, Success](action: AsyncOptAction[Error, Success])(register: (Error \/ Option[Success]) => Unit): Unit =
//    action.run.run.unsafePerformAsync {
//      case -\/(th)     => throw th
//      case \/-(result) => register(result)
//    }
//
//}
//
//trait ToAsyncOptActionOps {
//
//  implicit class ErrorHandler[B, A](action: AsyncOptAction[B, A]) {
//
//    def mapError[E, C](handler: B => C)(implicit ev1: ErrorCarrier[E], ev2: C <:< E): AsyncOptAction[E, A] = OptionT[({type λ[B] = EitherT[Task, E, B]})#λ, A](action.run leftMap (handler andThen (_.asInstanceOf[E])))
//
//  }
//
//  implicit class ToOptActionOps[E, A](action: AsyncOptAction[E, A]) {
//
//    def executeSync(): E \/ Option[A] = AsyncOptAction.runSync(action)
//
//    def executeAsync(register: (E \/ Option[A]) => Unit): Unit = AsyncOptAction.runAsync(action)(register)
//
//  }
//
//  implicit def toMt[A, E](a: =>A)(implicit ev: ErrorCarrier[E]): AsyncOptAction[E, A] = AsyncOptAction.return_(a)
//
//}
