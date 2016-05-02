package org.lolczak.async.error

import scala.language.implicitConversions

trait RecoverableErrorMatcher[E] {

  def isErrorRecoverable(error: E): Boolean

}

object RecoverableErrorMatcher {

  implicit def toMatcher[E](fun: E => Boolean): RecoverableErrorMatcher[E] = new RecoverableErrorMatcher[E]{
    override def isErrorRecoverable(error: E): Boolean = fun(error)
  }

}

class EveryErrorMatcher[E] extends RecoverableErrorMatcher[E] {
  override def isErrorRecoverable(error: E): Boolean = true
}