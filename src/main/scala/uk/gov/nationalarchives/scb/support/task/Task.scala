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

package uk.gov.nationalarchives.scb.support.task

import scala.reflect.{ClassTag, classTag}

/**
 * A general purpose Task. Unlike a Future, a Task has deferred execution,
 * but similar to an either in that it can return an Error or a Result.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
sealed abstract trait Task[+E, +T] {
  // TODO(AR) should we memoise the result on the first call? - could use a lazy val?
  def compute(): TaskResult[E, T]
  def bimap[EE, TT](fe: E => EE, ft: T => TT): Task[EE, TT]
}

class EitherTask[+E, +T](f: () => Either[E, T]) extends Task[E, T] {
  override def compute(): TaskResult[E, T] = f().fold(TaskFailed(_), TaskSuccess(_))
  override def bimap[EE, TT](fe: E => EE, ft: T => TT): Task[EE, TT] = {
    new EitherTask[EE, TT](() => f().left.map(fe).map(ft))
  }
}

// the ClassTag captures the type of E for pattern matching
class TryETask[+E <: Throwable: ClassTag, +T](f: () => T) extends Task[E, T] {
  override def compute(): TaskResult[E, T] = {
    scala.util.control.Exception.handling[TaskResult[E, T]](classTag[E].runtimeClass)
      .by(t => TaskFailed(t.asInstanceOf[E]))
      .apply{ TaskSuccess(f()) }
  }

  override def bimap[EE, TT](fe: E => EE, ft: T => TT): Task[EE, TT] = {
    new EitherTask[EE, TT] (
      () =>
        scala.util.control.Exception.catching[TT](classTag[E].runtimeClass)
          .either{ ft(f()) }
          .left.map(_.asInstanceOf[E])
          .left.map(fe)
    )
  }
}

//  class DefaultTask[+E, +T](f: Either[() => T, () => Either[E, T]]) {
//    def compute(): TaskResult[E, T] = {
//      f match {
//        case Left(l) =>
//          try {
//            TaskSuccess(l())
//          } catch {
//            case e: E =>
//              TaskFailed(e)
//          }
//        case Right(r) =>
//          r().fold(TaskFailed(_), TaskSuccess(_))
//      }
//    }
//  }

sealed abstract class TaskResult[+E, +T]
sealed case class TaskSuccess[+E, +T](t: T) extends TaskResult[E, T]
sealed case class TaskFailed[+E, +T](e: E) extends TaskResult[E, T]

object Task {
  // the ClassTag captures the type of E for pattern matching
  def make[E <: Throwable: ClassTag, T](f: () => T): Task[E, T] = new TryETask[E, T](f)
  def apply[E, T](f: () => Either[E, T]): Task[E, T] = new EitherTask[E, T](f)
  def applyE[E, T](f: () => E): Task[E, T] = new EitherTask[E, T](() => Left(f()))
  def applyT[E, T](f: () => T): Task[E, T] = new EitherTask[E, T](() => Right(f()))
}
