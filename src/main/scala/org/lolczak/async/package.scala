package org.lolczak

import scalaz.{OptionT, EitherT}
import scalaz.concurrent.Task

package object async {

  case class ErrorCarrier[A]()

  type AsyncAction[Error, Success] = EitherT[Task, Error, Success]

  type AsyncOptAction[Error, Success] = OptionT[({type λ[A] = EitherT[Task, Error, A]})#λ, Success]


}
