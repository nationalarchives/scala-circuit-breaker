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
import org.scalatest.matchers.must.Matchers.{an, convertToAnyMustWrapper, thrownBy}
import uk.gov.nationalarchives.scb.support.TryFunctorTestSupport.functor
import uk.gov.nationalarchives.scb.{ProtectedTask, TaskWithFuse}

import scala.reflect.ClassTag

/**
 * Support for using and testing the results of the Function0 Functor Adapter.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
object Function0FunctorTestSupport extends FunctorTestSupport[Function0] {
  override val testName = "Functor[Function0]"
  override val functor: Functor[Function0] = FunctorAdapter.functorForFunction0

  override def assertTaskWithFuseSucceeded[T](expectedResult: T)(protectedTask: => ProtectedTask[() => T]): Assertion = {
    val actualResult = protectedTask
    actualResult.isProtected mustBe true
    actualResult.fold[String](_ => "FOLD ERROR", t => t().toString) mustEqual expectedResult.toString
  }

  override def assertTaskWithFuseFailed[E <: Throwable: ClassTag](exception: E)(protectedTask: => ProtectedTask[Function0[_]]): Assertion = {
    an [E] mustBe thrownBy {
      val actualResult = protectedTask
      actualResult.fold[String](_ => "FOLD ERROR", t => t().toString)
    }
  }
}
