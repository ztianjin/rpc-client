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

class LoadBalancingChannel[T <: AnyRef](
  underlying: Seq[Client[T]],
  loadbalancer: (Seq[Client[T]] => Client[T]))
  (implicit manifest: Manifest[T])
extends Client[T] {

  def this(underlying: Seq[Client[T]])(implicit manifest: Manifest[T]) =
    this(underlying, new RandomLoadBalancer)

  val underlyingA = underlying.toArray

  def failoverClientException: PartialFunction[Exception, Boolean] = {
    case _ => true
  }

  def proxy = {
    Proxy[T](manifest.erasure) { invocation =>
      val attemptedServers = mutable.Set[Client[T]]()

      def failover: AnyRef = {
        val client = underlyingA.filter(_.isHealthy).filter(!attemptedServers.contains(_)) match {
          case Array() => throw new ClientUnavailableException
          case healthy => loadbalancer(healthy)
        }

        try { invocation(client.proxy) } catch { case e: Exception =>
          val shouldFailover =
            if (failoverClientException.isDefinedAt(e))
              failoverClientException(e)
            else
              true

          if (shouldFailover) {
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
