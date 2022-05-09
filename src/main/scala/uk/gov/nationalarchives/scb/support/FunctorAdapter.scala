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

import scala.util.Try

/**
 * Adapters from various Types to Functors.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
object FunctorAdapter {

  /**
   * Adapts a Try to a Functor[Try].
   */
  implicit val functorForTry: Functor[Try] = new Functor[Try] {

    override def pure[A](a: => A): Try[A] = Try(a)

    override def map[A, B](fa: Try[A])(f: A => B): Try[B] = {
      // map the try
      fa.map(f(_))
    }

    override def failed[A](exception: Throwable): Try[A] = {
      pure(throw exception)
    }

    override def recoverWith[A, U >: A](fa: Try[A])(pf: PartialFunction[Throwable, Try[U]]): Try[U] = {
      fa.recoverWith(pf)
    }
  }
}
