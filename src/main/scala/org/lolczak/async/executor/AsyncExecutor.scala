package org.lolczak.async.executor

import org.lolczak.async.error.{EveryErrorMatcher, RecoverableErrorMatcher}
import org.lolczak.async.{AsyncOptAction, AsyncAction}

import scala.concurrent.Future
import scalaz.\/

trait AsyncExecutor {

  def execute[E, A](action: AsyncAction[E, A])(implicit isErrRecoverable: RecoverableErrorMatcher[E] = EveryErrorMatcher): Future[E \/ A]

  def executeOpt[E, A](optAction: AsyncOptAction[E, A])(implicit isErrRecoverable: RecoverableErrorMatcher[E] = EveryErrorMatcher): Future[E \/ Option[A]]

}
