/*
 * Copyright (c) 2022 The National Archives
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.gov.nationalarchives.scb

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.nationalarchives.scb.support.FunctorTestSupport

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Spec behaviours for a Circuit Breaker.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
trait CircuitBreakerBehaviours extends AnyWordSpec with Matchers {

  def inClosedState[F[_], T](fts: FunctorTestSupport[F], newCircuitBreaker: => TestableCircuitBreaker): Unit = {
    "in the CLOSED state" should {
      "return a result from a successful task" in {
        val successResult = s"OK-${uuid()}"
        val task = fts.functor.pure(successResult)
        fts.assertTaskWithFuseSucceeded(successResult) {
          newCircuitBreaker.protect(task)(fts.functor)
        }
      }

      "return the exception from an unsuccessful task" in {
        val failureResult = OperationFailedException()
        val task = fts.functor.pure[T](throw failureResult)

        fts.assertTaskWithFuseFailed(failureResult) {
          newCircuitBreaker.protect(task)(fts.functor)
        }
      }

      "switch to the OPEN state when maxFailures is exceeded" in {
        val breaker = newCircuitBreaker
        val countingListener = CountingListener()
        breaker.addListener(countingListener)

        // throw OperationFailedException upto maxFailures
        val failureResult = OperationFailedException()
        val task = fts.functor.pure[T](throw failureResult)
        for (_ <- 0 to breaker.getMaxFailures()) {
          fts.assertTaskWithFuseFailed(failureResult) {
            breaker.protect(task)(fts.functor)
          }
        }

        // next OperationFailedException should exceed maxFailures, causing the transition to OPEN state, and therefore throwing ExecutionRejectedException
        val successResult = s"OK-${uuid()}"
        val task2 = fts.functor.pure(successResult)
        val actualResult = breaker.protect(task2)(fts.functor)
        actualResult mustBe a [RejectedTask[_]]

        countingListener.closed mustBe 0
        countingListener.halfOpened mustBe 0
        countingListener.opened mustBe 1
      }
    }
  }

  def inOpenState[F[_]](fts: FunctorTestSupport[F], newCircuitBreaker: => CircuitBreaker): Unit = {
    "in the OPEN state" should {
      "reject all tasks" in {
        val taskWasRun = new AtomicBoolean(false)
        def task = fts.functor.pure {
          taskWasRun.set(true)
          s"OK-${uuid()}"
        }
        val actualResult = newCircuitBreaker.protect(task)(fts.functor)
        actualResult mustBe a [RejectedTask[_]]
        taskWasRun.get() mustBe false
      }
    }
  }

  def inResetTimeoutState[F[_], T](fts: FunctorTestSupport[F], newCircuitBreaker: => TestableCircuitBreaker): Unit = {
    "in the RESET_TIMEOUT state" should {
      "accept the first task, and switch to HALF_OPEN and then CLOSED if task succeeds" in {
        val breaker = newCircuitBreaker
        val countingListener = CountingListener()
        breaker.addListener(countingListener)

        val successResult = s"OK-${uuid()}"
        val task = fts.functor.pure(successResult)
        fts.assertTaskWithFuseSucceeded(successResult) {
          breaker.protect(task)(fts.functor)
        }
        breaker.getInternalState() mustEqual InternalState.CLOSED

        countingListener.halfOpened mustEqual 1
        countingListener.closed mustEqual 1
      }

      "accept the first task, and switch to HALF_OPEN and then OPEN if task fails" in {
        val breaker = newCircuitBreaker
        val countingListener = CountingListener()
        breaker.addListener(countingListener)

        val failureResult = OperationFailedException()
        val task = fts.functor.pure[T](throw failureResult)
        fts.assertTaskWithFuseFailed(failureResult) {
          breaker.protect(task)(fts.functor)
        }
        breaker.getInternalState() mustEqual InternalState.OPEN

        countingListener.halfOpened mustEqual 1
        countingListener.opened mustEqual 1
      }
    }
  }

  def inHalfOpenState[F[_]](fts: FunctorTestSupport[F], newCircuitBreaker: => TestableCircuitBreaker): Unit = {
    "in the HALF_OPEN state" should {
      "reject all tasks" in {
        val successResult = s"OK-${uuid()}"
        val task = fts.functor.pure(successResult)
        val actualResult = newCircuitBreaker.protect(task)(fts.functor)
        actualResult mustBe a [RejectedTask[_]]
      }
    }
  }

  private def uuid() = UUID.randomUUID().toString

  class OperationFailedException extends Throwable
  object OperationFailedException {
    def apply() = new OperationFailedException()
  }

  case class CountingListener(var closed: Int = 0, var halfOpened: Int = 0, var opened: Int = 0) extends Listener {
    override def onClosed(): Unit = closed += 1
    override def onHalfOpen(): Unit = halfOpened += 1
    override def onOpen(): Unit = opened += 1
  }
}
