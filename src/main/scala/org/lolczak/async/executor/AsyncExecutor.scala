package org.lolczak.async.executor

import org.lolczak.async.{AsyncOptAction, AsyncAction}

import scala.concurrent.Future
import scalaz.\/

trait AsyncExecutor {

  def execute[E, A](action: AsyncAction[E, A]): Future[E \/ A]

  def execute[E, A](optAction: AsyncOptAction[E, A]): Future[E \/ Option[A]]

}
