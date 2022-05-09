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

import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import uk.gov.nationalarchives.scb.{ProtectedTask, TaskWithFuse}

import scala.reflect.ClassTag
import scala.util.{Failure, Try}

/**
 * Support for using and testing the results of the Try Functor Adapter.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
object TryFunctorTestSupport extends FunctorTestSupport[Try] {
  override val testName = "Functor[Try]"
  override val functor: Functor[Try] = FunctorAdapter.functorForTry

  override def assertTaskWithFuseSucceeded[T](expectedResult: T)(protectedTask: => ProtectedTask[Try[T]]): Assertion = {
    val actualResult = protectedTask
    actualResult.isProtected mustBe true
    actualResult mustEqual TaskWithFuse(functor.pure(expectedResult))
  }

  override def assertTaskWithFuseFailed[E <: Throwable: ClassTag](exception: E)(protectedTask: => ProtectedTask[Try[_]]): Assertion = {
    protectedTask mustEqual TaskWithFuse(Failure(exception))
  }
}
