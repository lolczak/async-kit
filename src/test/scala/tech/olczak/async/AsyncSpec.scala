package tech.olczak.async

import tech.olczak.async.Async._
import tech.olczak.async.error.ThrowableMapper
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Future
import scalaz.{-\/, \/-, _}

class AsyncSpec extends WordSpec with Matchers {

  "Async" should {
    "fork block of code" in {
      //when
      val \/-(result) = fork { Thread.currentThread().getId } executeSync()
      //then
      result shouldNot be(Thread.currentThread().getId)
    }

    "map errors" in {
      //when
      val result = fork { throw new RuntimeException("err") } mapError { case th => Failure(th.getMessage) } executeSync()
      //then
      result shouldBe -\/(Failure("err"))
    }

    "recover errors" in {
      //when
      val result = raiseError(Failure("err")) recoverWith { case th => return_("OK") } executeSync()
      //then
      result shouldBe \/-("OK")
    }

    "convert action to proper failure" in {
      val action = for {
        res1 <- delay(1) mapError { case th => Failure(th.getMessage) }
        res2 <- return_(3)
      } yield res1 + res2

      action.executeSync() shouldBe \/-(4)
    }

    "find upper bound of failure" in {
      trait Err
      case object Err1 extends Err
      case object Err2 extends Err

      val action: Async[Err, Int] =
        for {
          res1 <- delay(1) mapError[Err] { case th => Err1 }
          res2 <- delay(1) mapError { case th => Err2 }
        } yield res1 + res2

      action.executeSync() shouldBe \/-(2)
    }

    "find upper bound for error and success type" in {
      val action: Async[TestFailure, Unit] = for {
        res1 <- delay(1) mapError { case th => TestFailure(th.getMessage) }
        res2 <- if (res1 == 1) return_()
                else raiseError(TestFailure("err"))
      } yield res2

      action.executeSync() shouldBe \/-()
    }

    "support bind syntax by default" in {
      delay(1) >> delay(2) >>! (_ => delay(3)) executeSync() shouldBe \/-(2)
    }

    "catch exceptions during registering async listener" in {
      val ex = new Exception
      async[Unit](l => throw ex) executeSync() shouldBe -\/(ex)
    }

    "defer block of code" in {
      implicit val thMapper: ThrowableMapper[String] = (th: Throwable) => th.toString
      defer { 1 } executeSync() shouldBe \/-(1)
    }

    "defer future start" in {
      import scala.concurrent.ExecutionContext.Implicits.global
      var i = 1
      val action = spawn { () => Future { i = 2; i } }
      i shouldBe 1
      action.executeSync() shouldBe \/-(2)
      i shouldBe 2
    }

  }

  "Async" when {

    "error type is equal to Exception" should {

      "support monad syntax without sophisticated imports" in {
        (delay(1) >>= { result => delay(result + 1) }) executeSync() shouldBe \/-(2)
      }

      "support apply syntax without sophisticated imports" in {
        (delay(1) ⊛ delay(2)) ((x,y) => x + y) executeSync() shouldBe \/-(3)
      }

    }
  }

}

