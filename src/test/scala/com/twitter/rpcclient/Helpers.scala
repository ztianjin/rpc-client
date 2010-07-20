// Copyright 2010 Twitter, Inc.

package com.twitter.rpcclient

import java.util.concurrent.CyclicBarrier

object Helpers {
  trait TestRpcClient {
    def call:String = "hey"
    def waitCall(barrier:CyclicBarrier) = {
      barrier.await()
      call
    }

    // Java proxies are finicky about exceptions, so we need to
    // annotate.
    @throws(classOf[Exception])
    def exceptionCall(e:Exception) {
      throw e
    }
  }

  class TestConnection extends Connection[TestRpcClient] {
    var _isHealthy      = true
    var ensureOpenCount = 0
    var teardownCount   = 0
    var flushCount      = 0
    val host            = "test"
    val port            = 0

    def makeUnhealthy() { _isHealthy = false }

    val client = new TestRpcClient {}
    def ensureOpen() { ensureOpenCount += 1 }
    def teardown()   {   teardownCount += 1 }
    def flush()      {      flushCount += 1 }

    override def isHealthy = _isHealthy
  }
}
