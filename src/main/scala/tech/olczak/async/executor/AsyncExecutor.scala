package tech.olczak.async.executor

import tech.olczak.async.error.{EveryErrorMatcher, RecoverableErrorMatcher}
import tech.olczak.async.{AsyncOpt, Async}

import scala.concurrent.Future
import scalaz.\/

trait AsyncExecutor {

  def execute[E, A](action: Async[E, A])(implicit isErrRecoverable: RecoverableErrorMatcher[E] = EveryErrorMatcher): Future[E \/ A]

  def executeOpt[E, A](optAction: AsyncOpt[E, A])(implicit isErrRecoverable: RecoverableErrorMatcher[E] = EveryErrorMatcher): Future[E \/ Option[A]]

}
