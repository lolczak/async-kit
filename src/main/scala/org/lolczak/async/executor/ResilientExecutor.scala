package org.lolczak.async.executor

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}

import com.typesafe.scalalogging.slf4j.LazyLogging
import org.lolczak.async.AsyncAction._
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

  override def execute[E, A](action: AsyncAction[E, A]): Future[\/[E, A]] = {
    val recoverableAction = action recoverWith recovery(action)(1, System.currentTimeMillis()) //todo refactor
    val promise = Promise[E \/ A]()
    recoverableAction.run.unsafePerformAsync {
      case -\/(th)     => promise.failure(th)
      case \/-(result) => promise.success(result)
    }
    promise.future
  }

  private def recovery[E, A](action: AsyncAction[E, A])(retryCount: Int, startTimeMs: Long): PartialFunction[E, AsyncAction[E, A]] = {
    //todo check limits!!!
    case failure if retryCount <= maxRetries =>
      val waitTime = backoffTimeCalculator.evalBackoffTime(retryCount, FiniteDuration(System.currentTimeMillis() - startTimeMs, TimeUnit.MILLISECONDS))
      val recoverableAction =
        for {
          _ <- logger.error(s"Error occurred during action execution $failure. Retrying $retryCount time after $waitTime.").asAsyncAction[E]
          _ <- schedule[E](logger.warn(s"Retrying $retryCount time"), waitTime)
          result <- action
        } yield result
      recoverableAction recoverWith recovery(action)(retryCount + 1, startTimeMs)
  }

  private def schedule[E](task: => Unit, waitTime: FiniteDuration): AsyncAction[E, Unit] =
    EitherT.eitherT(Task.schedule(task, waitTime) map { case result => \/-().asInstanceOf[E \/ Unit] })

  override def execute[E, A](optAction: AsyncOptAction[E, A]): Future[\/[E, Option[A]]] = ???
}

object ResilientExecutor {
  val MaxRetriesLimit = 20
}
