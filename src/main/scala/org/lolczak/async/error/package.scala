package org.lolczak.async

package object error {

  type RecoverableErrorMatcher[-E] = E => Boolean

  val EveryErrorMatcher: RecoverableErrorMatcher[Any] = _ => true

  trait ThrowableMapper[+E] {
    def mapThrowable(th: Throwable): E
  }

  implicit def funToThMapper[E](f: Throwable => E): ThrowableMapper[E] = new ThrowableMapper[E] {
    override def mapThrowable(th: Throwable): E = f(th)
  }

}
