# Scala Circuit Breaker

[![Build Status](https://github.com/nationalarchives/scala-circuit-breaker/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/nationalarchives/scala-circuit-breaker/actions/workflows/ci.yml)
[![Scala 2.13](https://img.shields.io/badge/scala-2.13-red.svg)](http://scala-lang.org)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/uk.gov.nationalarchives/scala-circuit-breaker_2.13/badge.svg)](https://search.maven.org/search?q=g:uk.gov.nationalarchives)

The Circuit Breaker pattern implemented as a library in Scala.

We provide two implementations:
1. `StandardCircuitBreaker` which is not thread-safe and should only be used from a single-thread of have access to it synchronised.
2. `ThreadSafeCircuitBreaker` which is thread-safe and may be used from multiple threads concurrently.

Both implementations may be safely used in the same application for different purposes.

The Circuit Breaker implementations have been developed in such a way that they operate over an Abstract Type called `Functor`.
Through this mechanism it is possible for the Circuit Breakers to work directly with your own Data Types,
you just need to provide an implementation of `uk.gov.nationalarchives.scb.support.Functor`.

We provide adapters (in `uk.gov.nationalarchives.scb.support.FunctorAdapter`) that you may import that provide implementations for Scala's `Try` and `Future` types.

## Use

You need to add the following dependency to your `build.sbt`:

```scala
"uk.gov.nationalarchives" %% "scala-circuit-breaker" % "1.0.1"
```

## Examples

### Example using `Try`

```scala
import uk.gov.nationalarchives.scb._
import uk.gov.nationalarchives.scb.support.FunctorAdapter.functorForTry

import scala.concurrent.duration.DurationInt
import java.io.IOException
import scala.util.{Failure, Success, Try}


  @throws[IOException]
  def yourTask: String = ???

  val circuitBreaker = StandardCircuitBreaker("my-breaker-1", maxFailures = 5, resetTimeout = 120.seconds, exponentialBackoffFactor = 2, maxResetTimeout = 10.minutes)

  circuitBreaker.addListener(new Listener {
    override def onClosed(): Unit = println("Circuit Breaker now closed... you can take action on this event if you like!")
    override def onHalfOpen(): Unit = println("Circuit Breaker now half-open... you can take action on this event if you like!")
    override def onOpen(): Unit = println("Circuit Breaker now open... you can take action on this event if you like!")
  })

  val yourProtectedTask = circuitBreaker.protect(Try(yourTask))

  yourProtectedTask match {
     case TaskWithFuse(task) => task match {
       case Success(result) => println(s"Your task succeeded: $result")
       case Failure(t) => println(s"Your task failed: $t")
     }
     case RejectedTask(reason) => println(s"Task was rejected, Circuit Breaker is open: $reason")
   }
```

### Example using `Future`

```scala
import uk.gov.nationalarchives.scb.{Listener, RejectedTask, StandardCircuitBreaker, TaskWithFuse}

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.nationalarchives.scb.support.FunctorAdapter.functorForFuture

import java.io.IOException
import scala.concurrent.Future


  @throws[IOException]
  def yourTask: String = ???

  val circuitBreaker = StandardCircuitBreaker("my-breaker-1", maxFailures = 5, resetTimeout = 120.seconds, exponentialBackoffFactor = 2, maxResetTimeout = 10.minutes)

  circuitBreaker.addListener(new Listener {
    override def onClosed(): Unit = println("Circuit Breaker now closed... you can take action on this event if you like!")
    override def onHalfOpen(): Unit = println("Circuit Breaker now half-open... you can take action on this event if you like!")
    override def onOpen(): Unit = println("Circuit Breaker now open... you can take action on this event if you like!")
  })

  val yourProtectedTask = circuitBreaker.protect(Future { yourTask })

  yourProtectedTask match {
     case TaskWithFuse(task) =>
       task
         .map(result => println(s"Your task succeeded: $result"))
         .recover { case t: Throwable => println(s"Your task failed: $t")}

     case RejectedTask(reason) => println(s"Task was rejected, Circuit Breaker is open: $reason")
   }
```

## Building from Source Code

### Pre-requisites for building the project:
* [sbt](https://www.scala-sbt.org/) >= 1.6.2  
* [Scala](https://www.scala-lang.org/) >= 2.13.8
* [Java JDK](https://adoptopenjdk.net/) >= 1.8
* [Git](https://git-scm.com)

### Build steps:
1. Clone the Git repository:
```
git clone https://github.com/nationalarchives/scala-circuit-breaker.git
```
2. Compile and install to your local Ivy or Maven repository:
```
sbt clean compile publishLocal
```
After this, other builds on the same machine can depend on it by modifying their `build.sbt` files to include the SNAPSHOT dependency:

```scala
"uk.gov.nationalarchives" %% "scala-circuit-breaker" % "1.1.0-SNAPSHOT"
```

## Publishing a Release to Maven Central

1. Run `sbt clean release`
2. Answer the questions
3. Login to https://oss.sonatype.org/ then Close, and Release the Staging Repository
