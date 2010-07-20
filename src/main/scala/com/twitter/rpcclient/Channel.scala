// Copyright 2010 Twitter, Inc.
//
// A channel multiplexes requests over several underlying clients.

package com.twitter.rpcclient

import scala.util.Random

class LoadBalancingChannel[T <: AnyRef](underlying: Seq[Client[T]]) extends Client[T] {
  val underlyingA = underlying.toArray
  val rng = new Random

  def proxy = {
    // Send the request to a random healthy client.
    underlyingA.filter(_.isHealthy) match {
      case Array() => throw new ClientUnavailableException
      case healthy => healthy(rng.nextInt(healthy.size)).proxy
    }
  }

  def isHealthy = underlyingA.exists(_.isHealthy)
}
