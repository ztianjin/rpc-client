// Copyright 2010 Twitter, Inc.
//
// Tests for the RPCClient application health mixin.

package com.twitter.rpcclient

import org.specs._
import org.specs.mock.Mockito
import org.mockito.Matchers._
import org.mockito.Mock

import com.twitter.util.{Duration, Time}
import com.twitter.util.TimeConversions._

object ApplicationHealthSpec extends Specification with Mockito {
  import Helpers._

  "call into the application health check at specified intervals" in {
    val conn = new TestConnection with ApplicationHealth[TestRpcClient] {
      var _isApplicationHealthy = true
      var numChecks = 0
      def isApplicationHealthy = {
        numChecks += 1
        _isApplicationHealthy
      }
      val applicationCheckInterval = 10.seconds
    }

    Time.freeze()
    conn.isHealthy must beTrue          // this triggered a health check.
    conn.numChecks must be_==(1)

    conn._isApplicationHealthy = false
    conn.isHealthy must beTrue
    conn.numChecks must be_==(1)    

    Time.advance(11.seconds)
    conn.isHealthy must beFalse
    conn.numChecks must be_==(2)
  }
}
