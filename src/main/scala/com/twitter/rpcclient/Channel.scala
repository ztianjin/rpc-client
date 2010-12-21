// Copyright 2010 Twitter, Inc.
//
// A channel multiplexes requests over several underlying clients.

package com.twitter.rpcclient

import scala.collection.mutable
import scala.reflect.Manifest
import scala.util.Random
import java.util.Collections

class RandomLoadBalancer[T <: AnyRef] extends (Seq[Client[T]] => Client[T]) {
  val rng = new Random
  def apply(clients: Seq[Client[T]]) = clients(rng.nextInt(clients.size))
}

object LoadBalancingChannel {
  val DEFAULT_RETRIES = 0
}

import LoadBalancingChannel._

class LoadBalancingChannel[T <: AnyRef](
  underlying: Seq[Client[T]],
  loadbalancer: Seq[Client[T]] => Client[T],
  maxRetries: Int)
  (implicit manifest: Manifest[T])
extends Client[T] {
  def this(underlying: Seq[Client[T]], maxRetries: Int)(implicit manifest: Manifest[T]) =
    this(underlying, new RandomLoadBalancer, maxRetries)

  def this(underlying: Seq[Client[T]])(implicit manifest: Manifest[T]) =
    this(underlying, new RandomLoadBalancer, DEFAULT_RETRIES)

  val underlyingA = underlying.toArray

  // override to indicate which client exceptions are fatal and
  // should not be retried
  def fatalClientExceptions: PartialFunction[Exception, Boolean] = {
    case _ => false
  }

  protected def isFatalException(e: Exception) = {
    fatalClientExceptions.isDefinedAt(e) && fatalClientExceptions(e)
  }

  def proxy = {
    Proxy[T](manifest.erasure) { invocation =>
      val attemptedServers = mutable.Set[Client[T]]()
      var numRetries       = 0

      def failover: AnyRef = {
        val client = underlyingA.filter(_.isHealthy).filter(!attemptedServers.contains(_)) match {
          case Array() => throw new ClientUnavailableException
          case healthy => loadbalancer(healthy)
        }

        try { invocation(client.proxy) } catch { case e: Exception =>
          if (numRetries < maxRetries && !isFatalException(e)) {
            numRetries       += 1
            attemptedServers += client
            failover
          } else throw e
        }
      }

      failover
    }
  }

  def isHealthy = underlyingA.exists(_.isHealthy)
}
