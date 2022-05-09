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
import net.jcip.annotations.NotThreadSafe

import java.util.{Timer, TimerTask}
import scala.concurrent.duration.Duration

/**
 * A Circuit Breaker which is designed only to be accessed
 * from a single thread.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@NotThreadSafe
class StandardCircuitBreaker(val name: String, maxFailures: Int, resetTimeout: Duration, exponentialBackoffFactor: Int, maxResetTimeout: Duration, initialState: State.State) extends CircuitBreaker {

  import InternalState._

  private class ResetTimeoutTask extends TimerTask {
    // transition from OPEN -> RESET_TIMEOUT
    override def run(): Unit = state = RESET_TIMEOUT
  }

  protected var state = InternalState.from(initialState)
  private var failures: Int = 0
  private val resetTimer = new Timer(s"$name-ResetTimer")
  private var resetTimeoutTaskDelay : Long = resetTimeout.toMillis

  private var listeners = List[Listener]()

  override def addListener(listener: Listener): Unit = {
    listeners = listeners :+ listener
  }

  override def protect[F[_], T](task: F[T])(implicit functor: Functor[F]): ProtectedTask[F[T]] = {
    state match {
      case CLOSED =>
        execClosedTask(task)(functor)

      case OPEN =>
        RejectedTask("Circuit Breaker is Open")

      case RESET_TIMEOUT =>
        state = HALF_OPEN

        // notify listeners of state change
        listeners.map(_.onHalfOpen())

        // try and process a single item to see if we can reset the CircuitBreaker
        attemptReset(task)(functor)

      case HALF_OPEN =>
        RejectedTask("Circuit Breaker is Half-Open and attempting to reset")
    }
  }

  private def execClosedTask[F[_], T](task: F[T])(functor: Functor[F]): ProtectedTask[F[T]] = {
    val protectedTask = functor.recoverWith(functor.map(task) { value =>
      // MAP

      // reset the failures count
      failures = 0

      value

    }) { case exception: Throwable =>
      // RECOVER WITH

      // increment the failures counter
      failures += 1

      // if maxFailures is exceeded
      if (failures > maxFailures) {
        // transition from CLOSED -> OPEN
        state = OPEN

        // set the Reset Timer
        resetTimer.schedule(new ResetTimeoutTask(), resetTimeoutTaskDelay)

        // notify listeners of state change
        listeners.map(_.onOpen())
      }

      // return the exception of the task
      functor.failed[T](exception)
    }

    TaskWithFuse(protectedTask)
//    Try(task()) match {
//      case Success(value) =>
//        // reset the failures count
//        failures = 0
//
//        // return the value of the task
//        TaskWithFuse(value)
//
//      case Failure(exception) =>
//        // increment the failures counter
//        failures += 1
//
//        // if maxFailures is exceeded
//        if (failures > maxFailures) {
//          // transition from CLOSED -> OPEN
//          state = OPEN
//
//          // set the Reset Timer
//          resetTimer.schedule(new ResetTimeoutTask(), resetTimeoutTaskDelay)
//
//          // notify listeners of state change
//          listeners.map(_.onOpen())
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
      resetTimeoutTaskDelay = resetTimeout.toMillis

      // reset the failures count
      failures = 0

      // transition from HALF_OPEN -> CLOSED
      state = CLOSED

      // notify listeners of state change
      listeners.map(_.onClosed())

      // return the value of the task
      value

    }) { case exception: Throwable =>
      // RECOVER WITH

      // multiply the resetTimeoutTaskDelay by the exponentialBackoffFactor, up to the configured maxResetTimeout
      resetTimeoutTaskDelay = Math.min(maxResetTimeout.toMillis, resetTimeoutTaskDelay * exponentialBackoffFactor)

      // transition from HALF_OPEN -> OPEN
      state = OPEN

      // set the Reset Timer
      resetTimer.schedule(new ResetTimeoutTask(), resetTimeoutTaskDelay)

      // notify listeners of state change
      listeners.map(_.onOpen())

      // return the exception of the task
      functor.failed[T](exception)
    }

    TaskWithFuse(protectedTask)

//    Try(task()) match {
//      case Success(value) =>
//
//        // reset the resetTimeoutTaskDelay to the initial value of resetTimeout
//        resetTimeoutTaskDelay = resetTimeout.toMillis
//
//        // reset the failures count
//        failures = 0
//
//        // transition from HALF_OPEN -> CLOSED
//        state = CLOSED
//
//        // notify listeners of state change
//        listeners.map(_.onClosed())
//
//        // return the value of the task
//        TaskWithFuse(value)
//
//      case Failure(exception) =>
//        // multiply the resetTimeoutTaskDelay by the exponentialBackoffFactor, up to the configured maxResetTimeout
//        resetTimeoutTaskDelay = Math.min(maxResetTimeout.toMillis, resetTimeoutTaskDelay * exponentialBackoffFactor)
//
//        // transition from HALF_OPEN -> OPEN
//        state = OPEN
//
//        // set the Reset Timer
//        resetTimer.schedule(new ResetTimeoutTask(), resetTimeoutTaskDelay)
//
//        // notify listeners of state change
//        listeners.map(_.onOpen())
//
//        // return the exception of the task
//        throw exception
//    }
  }
}

object StandardCircuitBreaker {

  private var nextSerialNumber = 0

  def apply(maxFailures: Int, resetTimeout: Duration, exponentialBackoffFactor: Int, maxResetTimeout: Duration): StandardCircuitBreaker = {
    apply(maxFailures, resetTimeout, exponentialBackoffFactor, maxResetTimeout, State.CLOSED)
  }

  def apply(maxFailures: Int, resetTimeout: Duration, exponentialBackoffFactor: Int, maxResetTimeout: Duration, initialState: State.State): StandardCircuitBreaker = {
    val name = s"CircuitBreaker-${nextSerialNumber}"
    nextSerialNumber += 1
    apply(name, maxFailures, resetTimeout, exponentialBackoffFactor, maxResetTimeout, initialState)
  }

  def apply(name: String, maxFailures: Int, resetTimeout: Duration, exponentialBackoffFactor: Int, maxResetTimeout: Duration): StandardCircuitBreaker = {
    apply(name, maxFailures, resetTimeout, exponentialBackoffFactor, maxResetTimeout, State.CLOSED)
  }

  def apply(name: String, maxFailures: Int, resetTimeout: Duration, exponentialBackoffFactor: Int, maxResetTimeout: Duration, initialState: State.State): StandardCircuitBreaker = {
    new StandardCircuitBreaker(name, maxFailures, resetTimeout, exponentialBackoffFactor, maxResetTimeout, initialState)
  }
}

