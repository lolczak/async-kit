package org.lolczak.async

import org.scalatest.{Matchers, FlatSpec}
import AsyncOptAction._
import scalaz.{-\/, \/-}

class AsyncOptActionSpec extends FlatSpec with Matchers {

  val fun = functionsFor[Failure]
  import fun._

  "Async action" should "fork block of code" in {
    //when
    val \/-(result) = fork { Some(Thread.currentThread().getId) } executeSync()
    //then
    result shouldNot be (Some(Thread.currentThread().getId))
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
    result shouldBe \/-(Some("OK"))
  }

  it should "support filtering" in {
    //given
    def action(value: Int) =
      for {
        response <- fork { Some(value) } mapError { case th => Failure(th.getMessage) }
        if response == 5
        result   <- fork { Some(response * 2) } mapError { case th => Failure(th.getMessage) }
      } yield result
    //then
    action(5).executeSync() shouldBe \/-(Some(10))
    action(1).executeSync() shouldBe \/-(None)
  }

}
