package org.lolczak.async

import org.lolczak.async.error.ThrowableMapper
import org.scalatest.{Matchers, FlatSpec}
import AsyncAction._
import scala.concurrent.Future
import scalaz.{\/-, -\/}

class AsyncActionSpec extends FlatSpec with Matchers {

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

  it should "convert action to proper failure" in {
    val action = for {
      res1 <- delay(1) mapError { case th => Failure(th.getMessage) }
      res2 <- return_(3)
    } yield res1 + res2

    action.executeSync() shouldBe \/-(4)
  }

  it should "find upper bound of failure" in {
    trait Err
    case object Err1 extends Err
    case object Err2 extends Err

    val action: AsyncAction[Err , Int] =
      for {
        res1 <- delay(1) mapError[Err] { case th => Err1}
        res2 <- delay(1) mapError { case th => Err2}
      } yield res1 + res2

    action.executeSync() shouldBe \/-(2)
  }

  it should "find upper bound for error and success type" in {
    val action: AsyncAction[Failure, Unit] = for {
      res1 <- delay(1) mapError { case th => Failure(th.getMessage) }
      res2 <- if (res1 == 1) return_()
              else raiseError(Failure("err"))
    } yield res2

    action.executeSync() shouldBe \/-()
  }

  it should "support bind syntax by default" in {
    delay(1) >> delay(2) >>! (_ => delay(3)) executeSync() shouldBe \/-(2)
  }

  it should "catch exceptions during registering async listener" in {
    val ex = new Exception
    async[Unit](l => throw ex) executeSync() shouldBe -\/(ex)
  }

  it should "defer block of code" in {
    implicit val thMapper: ThrowableMapper[String] = (th:Throwable) => th.toString
    defer { 1 } executeSync() shouldBe \/-(1)
  }

  it should "defer future start" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    var i = 1
    val action = asyncF { () => Future { i=2; i} }
    i shouldBe 1
    action.executeSync() shouldBe \/-(2)
    i shouldBe 2
  }

}

