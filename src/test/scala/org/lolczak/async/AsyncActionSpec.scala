package org.lolczak.async

import org.scalatest.{Matchers, FlatSpec}
import AsyncAction._

import scalaz.{-\/, \/-}

class AsyncActionSpec extends FlatSpec with Matchers {
  val fun = functionsFor[Failure]
  import fun._

  "Async action" should "fork block of code" in {
    //when
    val \/-(result) = fork { Thread.currentThread().getId } executeSync()
    //then
    result shouldNot be (Thread.currentThread().getId)
  }

  it should "map errors" in {
    //when
    val result = fork { throw new RuntimeException("err") } mapError { case th => Failure(th.getMessage) } executeSync()
    //then
    result shouldBe -\/(Failure("err"))
  }

  it should "recover errors" in {
    //when
    val result = raiseError(Failure("err")) recoverWith { case th => return_("OK") } executeSync()
    //then
    result shouldBe \/-("OK")
  }


}

case class Failure(msg: String)