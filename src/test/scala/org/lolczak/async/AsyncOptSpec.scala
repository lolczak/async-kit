package org.lolczak.async

import org.lolczak.async.AsyncOpt._
import org.lolczak.async.error.ThrowableMapper
import org.scalatest.{FlatSpec, Matchers}

import scalaz.{-\/, \/-}

class AsyncOptSpec extends FlatSpec with Matchers {

  "Async action" should "fork block of code" in {
    //when
    val \/-(result) = fork { Some(Thread.currentThread().getId) } executeSync()
    //then
    result shouldNot be(Some(Thread.currentThread().getId))
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
    def action(value: Int): AsyncOpt[Failure, String] =
      for {
        response <- fork { Some(value) } mapError { case th => Failure(th.getMessage) }
        if response == 5
        result   <- fork { Some((response * 2).toString) } mapError { case th => Failure(th.getMessage) }
      } yield result
    //then
    action(5).executeSync() shouldBe \/-(Some("10"))
    action(1).executeSync() shouldBe \/-(None)
  }

  it should "convert action to proper failure" in {
    val action = for {
      res1 <- delay(1) mapError { case th => Failure(th.getMessage) }
      res2 <- return_(3)
    } yield res1 + res2

    action.executeSync() shouldBe \/-(Some(4))
  }

  it should "find upper bound of failure" in {
    trait Err
    case object Err1 extends Err
    case object Err2 extends Err

    val action: AsyncOpt[Err , Int] =
      for {
        res1 <- delay(1) mapError[Err] { case th => Err1}
        res2 <- delay(1) mapError { case th => Err2}
      } yield res1 + res2

    action.executeSync() shouldBe \/-(Some(2))
  }


  it should "find upper bound for error and success type" in {
    val action: AsyncOpt[Failure, Unit] = for {
      res1 <- delay(1) mapError { case th => Failure(th.getMessage) }
      res2 <- if (res1 == 1) returnSome(()).asAsyncOptAction[Failure]
              else raiseError[Failure, Unit](Failure("err"))
    } yield res2

    action.executeSync() shouldBe \/-(Some())
  }

  it should "support bind syntax by default" in {
    delay(1) >> delay(2) >>! (_ => delay(3)) executeSync() shouldBe \/-(Some(2))
  }

  it should "catch exceptions during registering async listener" in {
    val ex = new Exception
    async[Unit](l => throw ex) executeSync() shouldBe -\/(ex)
  }

  it should "defer block of code" in {
    implicit val thMapper: ThrowableMapper[String] = (th:Throwable) => th.toString
    defer { 1 } executeSync() shouldBe \/-(Some(1))
  }

}
