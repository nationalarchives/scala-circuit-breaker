# Scala Circuit Breaker

[![Build Status](https://github.com/nationalarchives/scala-circuit-breaker/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/nationalarchives/scala-circuit-breaker/actions/workflows/ci.yml)
[![Scala 2.13](https://img.shields.io/badge/scala-2.13-red.svg)](http://scala-lang.org)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/uk.gov.nationalarchives.pdi/scala-circuit-breaker/badge.svg)](https://search.maven.org/search?q=g:uk.gov.nationalarchives)

The Circuit Breaker pattern implemented as a library in Scala.

## Use

You need to add the following dependency to your `build.sbt`:

```scala
"uk.gov.nationalarchives" %% "scala-circuit-breaker" % "1.0.0"
```

## Example

Support for using the Scala Circuit Breaker with `Try` or `Future` is baked in.

An example for those of you working with `Try`:
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
After this other builds on the same machine can depend on it:
```
libraryDependencies += "uk.gov.nationalarchives" %% "scala-circuit-breaker" % "1.0.0-SNAPSHOT"
```

## Publishing a Release to Maven Central

