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

/**
 * Interface for a simple Circuit Breaker.
 *
 * The protect method takes a task and executes
 * it in a manner that may change the state of
 * the Circuit Breaker depending on the outcome
 * of the task.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
trait CircuitBreaker {

  /**
   * The name of the Circuit Breaker.
   */
  def name: String

  /**
   * Add a listener to the Circuit Breaker.
   *
   * @param listener the listener to add
   */
  def addListener(listener: Listener): Unit

  /**
   * Protect a Task with the Circuit Breaker.
   *
   * @tparam U The type of the result that the task produces.
   *
   * @param task the task to protect.
   *
   * @return the result of the task.
   */
  def protect[F[_], T](task: => F[T])(implicit functor: Functor[F]): ProtectedTask[F[T]]
}

/**
 * Result of calling [[CircuitBreaker.protect[U](() => U)]].
 */
sealed abstract class ProtectedTask[+T] {
  def isProtected: Boolean
  def isRejected: Boolean = !isProtected

  def fold[U](fe: String => U, ft: T => U): U = this match {
    case TaskWithFuse(t) => ft(t)
    case RejectedTask(reason)  => fe(reason)
  }
}
/**
 * Task has been accepted and is now protected
 * by the Circuit Breaker "fuse".
 *
 * @param t the value
 */
case class TaskWithFuse[+T](t: T) extends ProtectedTask[T] {
  override val isProtected: Boolean = true
}
/**
 * Task has been rejected.
 *
 * @param reason a description of why the task was rejected.
 */
case class RejectedTask[+T](reason: String) extends ProtectedTask[T] {
  override val isProtected: Boolean = false
}

/**
 * The observable state of the Circuit Breaker.
 */
object State extends Enumeration {
  type State = Value
  val CLOSED, OPEN, HALF_OPEN = Value
}

/**
 * The internal state of the Circuit Breaker.
 *
 * Similar to {@link State} except internally
 * we have an additional RESET_TIMEOUT state
 * to help differentiate between entering
 * the HALF_OPEN state and being in the
 * HALF_OPEN state.
 */
private[scb] object InternalState extends Enumeration {
  type InternalState = Value
  val CLOSED, OPEN, RESET_TIMEOUT, HALF_OPEN = Value

  /**
   * Maps from a State to an Internal State.
   *
   * @param state the state.
   *
   * @return the Internal State.
   */
  def from(state: State.State): InternalState = state match {
    case State.CLOSED => InternalState.CLOSED
    case State.OPEN => InternalState.OPEN
    case State.HALF_OPEN => InternalState.HALF_OPEN
  }
}

/**
 * Thrown when a Circuit Breaker rejects an incoming task.
 *
 * @param message the exception message.
 */
class ExecutionRejectedException(message: String) extends Throwable(message)

/**
 * Interface for a Listener which listens
 * to events produced by state changes in
 * a Circuit Breaker.
 */
trait Listener {
  def onClosed() : Unit
  def onHalfOpen() : Unit
  def onOpen() : Unit
}
