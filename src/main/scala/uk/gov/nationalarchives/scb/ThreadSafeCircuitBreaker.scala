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

import uk.gov.nationalarchives.scb.support.Functor
import net.jcip.annotations.ThreadSafe

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference}
import java.util.{Timer, TimerTask}
import scala.concurrent.duration.Duration

/**
 * A Circuit Breaker which is designed to be safely
 * accessed concurrently from multiple threads.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
class ThreadSafeCircuitBreaker(val name: String, maxFailures: Int, resetTimeout: Duration, exponentialBackoffFactor: Int, maxResetTimeout: Duration, initialState: State.State) extends CircuitBreaker {

  import InternalState._

  private class ResetTimeoutTask extends TimerTask {
    // transition from OPEN -> RESET_TIMEOUT
    override def run(): Unit = state.compareAndSet(OPEN, RESET_TIMEOUT)
  }

  protected val state = new AtomicReference[InternalState](InternalState.from(initialState))
  private val failures = new AtomicInteger(0)
  private val resetTimer = new Timer(s"$name-ResetTimer")
  private val resetTimeoutTaskDelay = new AtomicLong(resetTimeout.toMillis)

  private val listeners = new CopyOnWriteArrayList[Listener]

  override def addListener(listener: Listener): Unit = {
    listeners.add(listener)
  }

  override def protect[F[_], T](task: => F[T])(implicit functor: Functor[F]): ProtectedTask[F[T]] = {
    state.get() match {
      case CLOSED =>
        execClosedTask(task)(functor)

      case OPEN =>
        RejectedTask("Circuit Breaker is Open")

      case RESET_TIMEOUT =>
        if (state.compareAndSet(RESET_TIMEOUT, HALF_OPEN)) {

          // notify listeners of state change
          listeners.forEach(_.onHalfOpen())

          // try and process a single item to see if we can reset the CircuitBreaker
          attemptReset(task)(functor)

        } else {
          // state has been changed by another thread that preempted this thread, so try-again...
          protect(task)
        }

      case HALF_OPEN =>
        RejectedTask("Circuit Breaker is Half-Open and attempting to reset")
    }
  }

  private def execClosedTask[F[_], T](task: F[T])(functor: Functor[F]): ProtectedTask[F[T]] = {
    val protectedTask = functor.recoverWith(functor.map(task) { value =>
      // MAP

      // reset the failures count
      failures.set(0)

      // return the value of the task
      value

    }) { case exception: Throwable =>
      // RECOVER WITH

      // increment the failures counter
      val failedCount = failures.incrementAndGet()

      // if maxFailures is exceeded
      if (failedCount > maxFailures) {
        // transition from CLOSED -> OPEN
        if (state.compareAndSet(CLOSED, OPEN)) {

          // set the Reset Timer
          resetTimer.schedule(new ResetTimeoutTask(), resetTimeoutTaskDelay.get())

          // notify listeners of state change
          listeners.forEach(_.onOpen())
        }
      }

      // return the exception of the task
      functor.failed[T](exception)
    }

    TaskWithFuse(protectedTask)

//    Try(task()) match {
//      case Success(value) =>
//        // reset the failures count
//        failures.set(0)
//
//        // return the value of the task
//        TaskWithFuse(value)
//
//      case Failure(exception) =>
//        // increment the failures counter
//        val failedCount = failures.incrementAndGet()
//
//        // if maxFailures is exceeded
//        if (failedCount > maxFailures) {
//          // transition from CLOSED -> OPEN
//          if (state.compareAndSet(CLOSED, OPEN)) {
//
//            // set the Reset Timer
//            resetTimer.schedule(new ResetTimeoutTask(), resetTimeoutTaskDelay.get())
//
//            // notify listeners of state change
//            listeners.forEach(_.onOpen())
//          }
//        }
//
//        // return the exception of the task
//        throw exception
//    }
  }

  private def attemptReset[F[_], T](task: F[T])(functor: Functor[F]): ProtectedTask[F[T]] = {
    val protectedTask = functor.recoverWith(functor.map(task) { value =>
      // MAP

      // reset the resetTimeoutTaskDelay to the initial value of resetTimeout
      resetTimeoutTaskDelay.set(resetTimeout.toMillis)

      // reset the failures count
      failures.set(0)

      // transition from HALF_OPEN -> CLOSED
      if (state.compareAndSet(HALF_OPEN, CLOSED)) {
        // notify listeners of state change
        listeners.forEach(_.onClosed())
      } else {
        throw new IllegalStateException("Attempted to transition state HALF_OPEN -> CLOSED, but current state was not HALF_CLOSED")
      }

      // return the value of the task
      value

    }) { case exception: Throwable =>
      // RECOVER WITH

      // multiply the resetTimeoutTaskDelay by the exponentialBackoffFactor, up to the configured maxResetTimeout
      resetTimeoutTaskDelay.updateAndGet(value => Math.min(maxResetTimeout.toMillis, value * exponentialBackoffFactor))

      // transition from HALF_OPEN -> OPEN
      if (state.compareAndSet(HALF_OPEN, OPEN)) {
        // set the Reset Timer
        resetTimer.schedule(new ResetTimeoutTask(), resetTimeoutTaskDelay.get())

        // notify listeners of state change
        listeners.forEach(_.onOpen())
      }

      // return the exception of the task
      functor.failed[T](exception)
    }

    TaskWithFuse(protectedTask)

//    Try(task()) match {
//      case Success(value) =>
//
//        // reset the resetTimeoutTaskDelay to the initial value of resetTimeout
//        resetTimeoutTaskDelay.set(resetTimeout.toMillis)
//
//        // reset the failures count
//        failures.set(0)
//
//        // transition from HALF_OPEN -> CLOSED
//        if (state.compareAndSet(HALF_OPEN, CLOSED)) {
//          // notify listeners of state change
//          listeners.forEach(_.onClosed())
//        } else {
//          throw new IllegalStateException("Attempted to transition state HALF_OPEN -> CLOSED, but current state was not HALF_CLOSED")
//        }
//
//        // return the value of the task
//        TaskWithFuse(value)
//
//      case Failure(exception) =>
//        // multiply the resetTimeoutTaskDelay by the exponentialBackoffFactor, up to the configured maxResetTimeout
//        resetTimeoutTaskDelay.updateAndGet(value => Math.min(maxResetTimeout.toMillis, value * exponentialBackoffFactor))
//
//        // transition from HALF_OPEN -> OPEN
//        if (state.compareAndSet(HALF_OPEN, OPEN)) {
//          // set the Reset Timer
//          resetTimer.schedule(new ResetTimeoutTask(), resetTimeoutTaskDelay.get())
//
//          // notify listeners of state change
//          listeners.forEach(_.onOpen())
//        }
//
//        // return the exception of the task
//        throw exception
//    }
  }
}

object ThreadSafeCircuitBreaker {

  private val nextSerialNumber = new AtomicInteger()

  def apply(maxFailures: Int, resetTimeout: Duration, exponentialBackoffFactor: Int, maxResetTimeout: Duration): ThreadSafeCircuitBreaker = {
    apply(maxFailures, resetTimeout, exponentialBackoffFactor, maxResetTimeout, State.CLOSED)
  }

  def apply(maxFailures: Int, resetTimeout: Duration, exponentialBackoffFactor: Int, maxResetTimeout: Duration, initialState: State.State): ThreadSafeCircuitBreaker = {
    val name = s"CircuitBreaker-${nextSerialNumber.getAndIncrement()}"
    apply(name, maxFailures, resetTimeout, exponentialBackoffFactor, maxResetTimeout, initialState)
  }

  def apply(name: String, maxFailures: Int, resetTimeout: Duration, exponentialBackoffFactor: Int, maxResetTimeout: Duration): ThreadSafeCircuitBreaker = {
    apply(name, maxFailures, resetTimeout, exponentialBackoffFactor, maxResetTimeout, State.CLOSED)
  }

  def apply(name: String, maxFailures: Int, resetTimeout: Duration, exponentialBackoffFactor: Int, maxResetTimeout: Duration, initialState: State.State): ThreadSafeCircuitBreaker = {
    new ThreadSafeCircuitBreaker(name, maxFailures, resetTimeout, exponentialBackoffFactor, maxResetTimeout, initialState)
  }
}