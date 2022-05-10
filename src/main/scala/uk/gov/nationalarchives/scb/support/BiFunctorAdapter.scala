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

package uk.gov.nationalarchives.scb.support

import uk.gov.nationalarchives.scb.support.task.Task

/**
 * Adapters from various Types to BiFunctors.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
object BiFunctorAdapter {

  /**
   * Adapts a Task to a BiFunctor[Task].
   */
  implicit val biFunctorForTask: BiFunctor[Task] = new BiFunctor[Task] {

    override def pureA[A, B](a: => A): Task[A, B] = Task.applyE(() => a)

    override def pureB[A, B](b: => B): Task[A, B] = Task.applyT(() => b)

    override def bimap[E, T, EE, TT](F: Task[E, T])(fe: E => EE, ft: T => TT): Task[EE, TT] = {
      F.bimap(fe, ft)
    }
  }
}
