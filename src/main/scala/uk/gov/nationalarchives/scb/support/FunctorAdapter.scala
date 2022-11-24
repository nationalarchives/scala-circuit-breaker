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

import scala.concurrent.Future
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

  implicit val functorForFunction0: Functor[Function0] = new Functor[Function0] {
    var failure: Option[Throwable] = None

    override def pure[A](a: => A): () => A = () => a

    override def map[A, B](fa: Function0[A])(f: A => B): Function0[B] = {
      // TODO(AR) we would be better perhaps to use flatMap, so that we can defer the execution of fa()
      try {
        val a = fa()
        pure(f(a))
      } catch {
        case exception: Throwable =>
          this.failed[B](exception)
        //          () => exception.getMessage // TODO(AR) possible example of recovery which we can't compile!
      }
    }

    override def failed[A](exception: Throwable): Function0[A] = {
      this.failure = Some(exception)
      pure(throw exception)
    }

    override def recoverWith[A, U >: A](fa: Function0[A])(pf: PartialFunction[Throwable, Function0[U]]): Function0[U] = {
      failure match {
        case Some(exception) => pf.apply(exception)
        case None => fa
      }
    }
  }

  /**
   * Adapts a Future to a Functor[Future].
   */
  implicit val functorForFuture: Functor[Future] = new Functor[Future] {

    // TODO(AR) make the execution context parameterizable
    import scala.concurrent.ExecutionContext.Implicits.global

    override def pure[A](a: => A): Future[A] = Future { a }

    override def map[A, B](fa: Future[A])(f: A => B): Future[B] = {
      fa.map(f)
    }

    override def failed[A](exception: Throwable): Future[A] = {
      pure(throw exception)
    }

    override def recoverWith[A, U >: A](fa: Future[A])(pf: PartialFunction[Throwable, Future[U]]): Future[U] = {
      fa.recoverWith(pf)
    }
  }
}
