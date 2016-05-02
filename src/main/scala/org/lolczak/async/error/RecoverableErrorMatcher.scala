package org.lolczak.async.error

trait RecoverableErrorMatcher[E] {

  def isErrorRecoverable(error: E): Boolean

}

class EveryErrorMatcher[E] extends RecoverableErrorMatcher[E] {
  override def isErrorRecoverable(error: E): Boolean = true
}