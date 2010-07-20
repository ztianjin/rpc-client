// Copyright 2010 Twitter, Inc.
//
// Application layer health checking for RPC clients.

package com.twitter.rpcclient

import com.twitter.xrayspecs.{Duration, Time}
import com.twitter.xrayspecs.TimeConversions._

// A mixin to check application health at a given interval. Minimal
// complete definition: ``isApplicationHealthy'' and
// ``applicationCheckInterval''.
trait ApplicationHealth[T] extends Connection[T] {
  // This is a (potentially side-effecting) application layer health
  // check. This is called with an interval of at most
  // ``applicationCheckInterval''. ``ensureOpen'' has also been called
  // prior to invocation.
  def isApplicationHealthy(): Boolean
  val applicationCheckInterval: Duration

  private var lastApplicationCheck = Time.never
  private var wasApplicationHealthy = true
  override def isHealthy: Boolean = {
    if (!super.isHealthy)
      return false

    if (lastApplicationCheck.ago > applicationCheckInterval) {
      ensureOpen()
      lastApplicationCheck  = Time.now
      wasApplicationHealthy = isApplicationHealthy()
    }

    wasApplicationHealthy
  }
}
