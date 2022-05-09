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

import scala.concurrent.duration.DurationInt

/**
 * Testable extension of {@link ThreadSafeCircuitBreaker}.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
private[scb] class TestableThreadSafeCircuitBreaker(maxFailures: Int = 3, initialState: State.State = State.CLOSED) extends ThreadSafeCircuitBreaker(
  name ="TestableStandardCircuitBreaker",
  maxFailures = maxFailures,
  resetTimeout = 10.seconds,
  exponentialBackoffFactor = 1,
  maxResetTimeout = 90.seconds,
  initialState) with TestableCircuitBreaker {

  private[scb] override def setInternalState(internalState: InternalState.InternalState): Unit = {
    state.set(internalState)
  }

  private[scb] override def getInternalState(): InternalState.InternalState = {
    state.get
  }

  private[scb] override def getMaxFailures(): Int = maxFailures
}

private[scb] object TestableThreadSafeCircuitBreaker extends TestableCircuitBreakerFactory {
  override val testName = "ThreadSafeCircuitBreaker"
  override def apply(maxFailures: Int = 3, initialState: State.State = State.CLOSED) = new TestableThreadSafeCircuitBreaker(maxFailures, initialState)
}
