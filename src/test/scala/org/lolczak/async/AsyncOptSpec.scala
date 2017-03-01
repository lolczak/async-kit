package org.lolczak.async

import org.lolczak.async.AsyncOpt._
import org.lolczak.async.error.ThrowableMapper
import org.scalatest.{Matchers, WordSpec}

import scalaz.{-\/, \/-}

class AsyncOptSpec extends WordSpec with Matchers {
  
  "AsyncOpt" should {

    "fork block of code" in {
      //when
      val \/-(result) = fork { Some(Thread.currentThread().getId) } executeSync()
      //then
      result shouldNot be(Some(Thread.currentThread().getId))
    }

    "map errors" in {
      //when
      val result = fork { throw new RuntimeException("err") } mapError { case th => TestFailure(th.getMessage) } executeSync()
      //then
      result shouldBe -\/(TestFailure("err"))
    }

    "recover errors" in {
      //when
      val result = raiseError(TestFailure("err")) recoverWith { case th => return_("OK") } executeSync()
      //then
      result shouldBe \/-(Some("OK"))
    }

    "support filtering" in {
      //given
      def action(value: Int): AsyncOpt[TestFailure, String] =
        for {
          response <- fork { Some(value) } mapError { case th => TestFailure(th.getMessage) }
          if response == 5
          result <- fork { Some((response * 2).toString) } mapError { case th => TestFailure(th.getMessage) }
        } yield result
      //then
      action(5).executeSync() shouldBe \/-(Some("10"))
      action(1).executeSync() shouldBe \/-(None)
    }

    "convert action to proper failure" in {
      val action = for {
        res1 <- delay(1) mapError { case th => TestFailure(th.getMessage) }
        res2 <- return_(3)
      } yield res1 + res2

      action.executeSync() shouldBe \/-(Some(4))
    }

    "find upper bound of failure" in {
      trait Err
      case object Err1 extends Err
      case object Err2 extends Err

      val action: AsyncOpt[Err, Int] =
        for {
          res1 <- delay(1) mapError[Err] { case th => Err1 }
          res2 <- delay(1) mapError { case th => Err2 }
        } yield res1 + res2

      action.executeSync() shouldBe \/-(Some(2))
    }


    "find upper bound for error and success type" in {
      val action: AsyncOpt[TestFailure, Unit] = for {
        res1 <- delay(1) mapError { case th => TestFailure(th.getMessage) }
        res2 <- if (res1 == 1) returnSome(()).asAsyncOpt[TestFailure]
        else raiseError[TestFailure, Unit](TestFailure("err"))
      } yield res2

      action.executeSync() shouldBe \/-(Some())
    }

    "support bind syntax by default" in {
      delay(1) >> delay(2) >>! (_ => delay(3)) executeSync() shouldBe \/-(Some(2))
    }

    "catch exceptions during registering async listener" in {
      val ex = new Exception
      async[Unit](l => throw ex) executeSync() shouldBe -\/(ex)
    }

    "defer block of code" in {
      implicit val thMapper: ThrowableMapper[String] = (th: Throwable) => th.toString
      defer { 1 } executeSync() shouldBe \/-(Some(1))
    }

  }

  "AsyncOpt" when {

    "error type is equal to Exception" should {

      "support monad syntax without sophisticated imports" in {
        (delay(1) >>= { result => delay(result + 1) }) executeSync() shouldBe \/-(Some(2))
      }

      "support apply syntax without sophisticated imports" in {
        (delay(1) ⊛ delay(2)) ((x, y) => x + y) executeSync() shouldBe \/-(Some(3))
      }

    }
  }
}
