package org.lolczak.async

package object error {

  type RecoverableErrorMatcher[-E] = E => Boolean

  val EveryErrorMatcher: RecoverableErrorMatcher[Any] = _ => true

  type ThrowableMapper[+E] = Throwable => E

}
