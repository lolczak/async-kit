package org.lolczak

import scala.util.control.NonFatal
import scalaz.{-\/, \/, OptionT, EitherT}
import scalaz.concurrent.Task

package object async {

  type Async[Error, Success] = EitherT[Task, Error, Success]

  type AsyncOpt[Error, Success] = OptionT[EitherT[Task, Error, ?], Success]

  def attachErrorHandling[A](register: ((Throwable \/ A) => Unit) => Unit): ((Throwable \/ A) => Unit) => Unit = { listener: ((Throwable \/ A) => Unit) =>
    try register(listener)
    catch {
      case NonFatal(th) => listener(-\/(th))
    }
  }

}
