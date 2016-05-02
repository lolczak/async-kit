package org.lolczak.async.executor

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}

import com.typesafe.scalalogging.slf4j.LazyLogging
import org.lolczak.async.AsyncAction._
import org.lolczak.async.error.{EveryErrorMatcher, RecoverableErrorMatcher}
import org.lolczak.async.executor.ResilientExecutor.MaxRetriesLimit
import org.lolczak.async.{AsyncAction, AsyncOptAction}

import scala.concurrent.{Promise, Future}
import scala.concurrent.duration.FiniteDuration
import scalaz.concurrent.Task
import scalaz.{EitherT, \/-, -\/, \/}

class ResilientExecutor(maxRetries: Int, executionLimit: FiniteDuration, backoffTimeCalculator: BackoffTimeCalculator)
                       (implicit pool: ScheduledExecutorService)
  extends AsyncExecutor with LazyLogging {

  val retryLimit = Math.min(maxRetries, MaxRetriesLimit)

  override def execute[E, A](action: AsyncAction[E, A])(implicit errorMatcher: RecoverableErrorMatcher[E] = new EveryErrorMatcher[E]): Future[\/[E, A]] = {
    val recoverableAction = action recoverWith recovery(action)(1, System.currentTimeMillis()) //todo refactor
    val promise = Promise[E \/ A]()
    recoverableAction.run.unsafePerformAsync {
      case -\/(th)     => promise.failure(th)
      case \/-(result) => promise.success(result)
    }
    promise.future
  }

  private def recovery[E, A](action: AsyncAction[E, A])(retryCount: Int, startTimeMs: Long)(implicit errorMatcher: RecoverableErrorMatcher[E]): PartialFunction[E, AsyncAction[E, A]] = {
    case failure if !isAnyLimitExceeded(retryCount, startTimeMs) && errorMatcher.isErrorRecoverable(failure) =>
      val waitTime = backoffTimeCalculator.evalBackoffTime(retryCount, durationSince(startTimeMs))
      val recoverableAction =
        for {
          _      <- logger.error(s"Error occurred during action execution $failure. Retrying $retryCount time after $waitTime.").asAsyncAction[E]
          _      <- schedule[E](logger.warn(s"Retrying $retryCount time"), waitTime)
          result <- action
        } yield result
      recoverableAction recoverWith recovery(action)(retryCount + 1, startTimeMs)
  }

  private def isAnyLimitExceeded[A, E](retryCount: Int, startTimeMs: Long): Boolean = retryCount > retryLimit || durationSince(startTimeMs) > executionLimit

  private def durationSince(startTimeMs: Long) = FiniteDuration(System.currentTimeMillis() - startTimeMs, TimeUnit.MILLISECONDS)

  private def schedule[E](task: => Unit, waitTime: FiniteDuration): AsyncAction[E, Unit] =
    EitherT.eitherT(Task.schedule(task, waitTime) map { case result => \/-().asInstanceOf[E \/ Unit] })

  override def executeOpt[E, A](optAction: AsyncOptAction[E, A])(implicit errorMatcher: RecoverableErrorMatcher[E] = new EveryErrorMatcher[E]): Future[\/[E, Option[A]]] = ???
}

object ResilientExecutor {
  val MaxRetriesLimit = 20
}
