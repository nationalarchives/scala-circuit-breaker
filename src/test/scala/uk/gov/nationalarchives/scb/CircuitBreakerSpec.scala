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
import uk.gov.nationalarchives.scb.support.TryFunctorTestSupport

/**
 * Specs for a Circuit Breaker.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
class CircuitBreakerSpec extends AnyWordSpec with Matchers with CircuitBreakerBehaviours {

  private val maxFailures = 3

  // for each type of Circuit Breaker
  for (testableCircuitBreakerFactory <- Seq(TestableStandardCircuitBreaker, TestableThreadSafeCircuitBreaker)) {

    // for each type of FunctorAdapter
    val ftss = Seq(TryFunctorTestSupport)
    for (fts <- ftss) {
      s"A ${testableCircuitBreakerFactory.testName} over ${fts.testName}" when {

        inClosedState(fts, TestableClosedCircuitBreaker(testableCircuitBreakerFactory))

        inOpenState(fts, TestableOpenCircuitBreaker(testableCircuitBreakerFactory))

        inResetTimeoutState(fts, TestableResetTimeoutCircuitBreaker(testableCircuitBreakerFactory))

        inHalfOpenState(fts, TestableHalfOpenCircuitBreaker(testableCircuitBreakerFactory))
      }
    }
  }

  private def TestableClosedCircuitBreaker(testableCircuitBreakerFactory: TestableCircuitBreakerFactory): TestableCircuitBreaker = testableCircuitBreakerFactory(maxFailures, State.CLOSED)

  private def TestableOpenCircuitBreaker(testableCircuitBreakerFactory: TestableCircuitBreakerFactory): TestableCircuitBreaker = testableCircuitBreakerFactory(maxFailures, State.OPEN)

  private def TestableResetTimeoutCircuitBreaker(testableCircuitBreakerFactory: TestableCircuitBreakerFactory): TestableCircuitBreaker = {
    val cb = testableCircuitBreakerFactory(maxFailures, State.CLOSED)
    cb.setInternalState(InternalState.RESET_TIMEOUT)
    cb
  }

  private def TestableHalfOpenCircuitBreaker(testableCircuitBreakerFactory: TestableCircuitBreakerFactory): TestableCircuitBreaker = {
    val cb = testableCircuitBreakerFactory(maxFailures, State.CLOSED)
    cb.setInternalState(InternalState.HALF_OPEN)
    cb
  }
}
