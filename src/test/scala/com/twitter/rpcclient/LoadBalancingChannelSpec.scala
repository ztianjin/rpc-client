package com.twitter.rpcclient

import org.specs._
import org.specs.mock.Mockito
import org.mockito.Matchers._
import org.mockito.Mock


object LoadBalancingChannelSpec extends Specification with Mockito {
  import Helpers._

  val mockClient1 = mock[Client[TestRpcClient]]
  val mockClient2 = mock[Client[TestRpcClient]]
  val mockClient3 = mock[Client[TestRpcClient]]
  def simpleLoadBalancer[T](clients: Seq[Client[T]]) = clients.first

  val channel = new LoadBalancingChannel(Seq(mockClient1, mockClient2), simpleLoadBalancer, 3)

  class TestException extends RuntimeException

  "load balancing channel" should {
    "fail over to another client" in {

      mockClient1.isHealthy returns false
      mockClient2.isHealthy returns true

      mockClient1.proxy returns new TestRpcClient { override def call = "hey shouldn't see me" }
      mockClient2.proxy returns new TestRpcClient {}

      channel.proxy.call mustEqual "hey"
    }

    "throw when all clients are dead" in {
      mockClient1.isHealthy returns false
      mockClient2.isHealthy returns false

      channel.proxy.call must throwA[ClientUnavailableException]
    }

    "not fail over to the same host twice" in {
      mockClient1.isHealthy returns true
      mockClient1.proxy returns new TestRpcClient { override def call = { throw new TestException } }

      channel.proxy.call must throwA[ClientUnavailableException]
    }

    "pass an exception up" in {
      val exceptionPassingChannel = new LoadBalancingChannel(Seq(mockClient1), simpleLoadBalancer, 1) {
        override def fatalClientExceptions = {
          case _: TestException => true
        }
      }

      mockClient1.isHealthy returns true
      mockClient1.proxy returns new TestRpcClient { override def call = { throw new TestException } }

      exceptionPassingChannel.proxy.call must throwA[TestException]
    }

    "does not retry when retries is 0" in {
      val noRetryChannel = new LoadBalancingChannel(Seq(mockClient1, mockClient2), simpleLoadBalancer, 0)

      mockClient1.isHealthy returns true
      mockClient1.proxy returns new TestRpcClient { override def call = { throw new TestException } }

      noRetryChannel.proxy.call must throwA[TestException]
    }

    "retries a specified number of times" in {
      val oneRetryChannel = new LoadBalancingChannel(Seq(mockClient1, mockClient2, mockClient3), simpleLoadBalancer, 1)

      mockClient1.isHealthy returns true
      mockClient1.proxy returns new TestRpcClient { override def call = { throw new TestException } }
      mockClient2.isHealthy returns true
      mockClient2.proxy returns new TestRpcClient { override def call = { throw new TestException } }

      oneRetryChannel.proxy.call must throwA[TestException]
    }

    "only retries each host once" in {
      val tooManyRetriesChannel = new LoadBalancingChannel(Seq(mockClient1, mockClient2), simpleLoadBalancer, 10)

      mockClient1.isHealthy returns true
      mockClient1.proxy returns new TestRpcClient { override def call = { throw new TestException } }
      mockClient2.isHealthy returns true
      mockClient2.proxy returns new TestRpcClient { override def call = { throw new TestException } }

      tooManyRetriesChannel.proxy.call must throwA[ClientUnavailableException]
    }
  }
}
