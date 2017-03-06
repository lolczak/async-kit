package tech.olczak.async.executor

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}

import com.typesafe.scalalogging.slf4j.LazyLogging
import tech.olczak.async.Async._
import tech.olczak.async.error.{EveryErrorMatcher, RecoverableErrorMatcher}
import tech.olczak.async.executor.ResilientExecutor.MaxRetriesLimit
import tech.olczak.async.{Async, AsyncOpt}

import scala.concurrent.{Promise, Future}
import scala.concurrent.duration.FiniteDuration
import scalaz.concurrent.Task
import scalaz.{EitherT, \/-, -\/, \/}

class ResilientExecutor(maxRetries: Int, executionLimit: FiniteDuration, backoffTimeCalculator: BackoffTimeCalculator)
                       (implicit pool: ScheduledExecutorService)
  extends AsyncExecutor with LazyLogging {

  val retryLimit = Math.min(maxRetries, MaxRetriesLimit)

  override def execute[E, A](action: Async[E, A])(implicit isErrRecoverable: RecoverableErrorMatcher[E] = EveryErrorMatcher): Future[\/[E, A]] = {
    val recoverableAction = action recoverWith recovery(action)(1, System.currentTimeMillis()) //todo refactor
    val promise = Promise[E \/ A]()
    recoverableAction.run.unsafePerformAsync {
      case -\/(th)     => promise.failure(th)
      case \/-(result) => promise.success(result)
    }
    promise.future
  }

  private def recovery[E, A](action: Async[E, A])(retryCount: Int, startTimeMs: Long)(implicit isErrRecoverable: RecoverableErrorMatcher[E]): PartialFunction[E, Async[E, A]] = {
    case failure if !isAnyLimitExceeded(retryCount, startTimeMs) && isErrRecoverable(failure) =>
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

  private def schedule[E](task: => Unit, waitTime: FiniteDuration): Async[E, Unit] =
    EitherT.eitherT(Task.schedule(task, waitTime) map { case result => \/-().asInstanceOf[E \/ Unit] })

  override def executeOpt[E, A](optAction: AsyncOpt[E, A])(implicit isErrRecoverable: RecoverableErrorMatcher[E] = EveryErrorMatcher): Future[\/[E, Option[A]]] =
    execute(optAction.run)

}

object ResilientExecutor {
  val MaxRetriesLimit = 20
}
