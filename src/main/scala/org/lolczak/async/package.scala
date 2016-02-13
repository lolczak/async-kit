package org.lolczak

import scalaz.{OptionT, EitherT}
import scalaz.concurrent.Task

package object async {

  type AsyncAction[Error, Success] = EitherT[Task, Error, Success]

  type AsyncOptAction[Error, Success] = OptionT[EitherT[Task, Error, ?], Success]

}
