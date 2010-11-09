// Copyright 2010 Twitter, Inc.

package com.twitter.rpcclient

import scala.actors.Futures.future

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

import org.specs._
import org.specs.mock.Mockito
import org.mockito.Matchers._
import org.mockito.Mock

import com.twitter.util.{Time, Duration}
import com.twitter.util.TimeConversions._

object PooledClientSpec extends Specification with Mockito {
  import Helpers._

  class TestClient extends PooledClient[TestRpcClient] {
    val _numConnections = new AtomicInteger(0)
    val name = "test"

    class TestClientConnection extends TestConnection {
      val clientName = _numConnections.getAndIncrement

      override val client = new TestRpcClient {
        override def call = "client:%d".format(clientName)
      }
    }

    var connections: List[TestClientConnection] = Nil
    def numConnections = _numConnections.get

    def createConnection = synchronized {
      val conn = new TestClientConnection()
      connections ::= conn
      conn
    }
  }

  "connection management" should {
    "reuse connections" in {
      val client = new TestClient
      client.numConnections must be_==(0)
     
      for (_ <- 0 until 100) {
        client.proxy.call must be_==("client:0")
        client.numConnections must be_==(1)
      }
     
      client.poolSize must be_==(1)
      client.numConnections must be_==(1)
      val conn = client.connections.first
      conn.teardownCount must be_==(0)
      conn.flushCount must be_==(100)
    }
     
    "allow simultaneous connections" in {
      val barrier = new CyclicBarrier(2)
      val client  = new TestClient
     
      val call1 = future { client.proxy.waitCall(barrier) }
      val call2 = future { client.proxy.waitCall(barrier) }
     
      val results = call1() :: call2() :: Nil
     
      client.numConnections must be_==(2)
      results must haveTheSameElementsAs(List("client:0", "client:1"))
    }
     
    "deal with an unhealthy node" in {
      val conn = mock[Connection[TestRpcClient]]
      conn.client returns (new TestRpcClient {})
      conn.isHealthy returns false

      val client = new PooledClient[TestRpcClient] {
        val name = "test"
        def createConnection = conn
      }
     
      client.proxy.call must throwA(new ClientUnavailableException)
    }
     
    "propagate exceptions" in {
      val conn = mock[Connection[TestRpcClient]]
      conn.client returns (new TestRpcClient {})
      conn.isHealthy returns true
      conn.unwrapException returns { case _ => UnknownError }
     
      val client = new PooledClient[TestRpcClient] {
        val name = "test"
        def createConnection = conn
      }
     
      client.proxy.exceptionCall(new Exception("hey")) must throwA(new Exception("hey"))
    }
     
    "throw timeout exceptions" in {
      val conn = mock[Connection[TestRpcClient]]
      conn.client returns (new TestRpcClient {})
      conn.isHealthy returns true
      conn.unwrapException returns { case _ => TimeoutError }
     
      val client = new PooledClient[TestRpcClient] {
        val name = "test"
        def createConnection = conn
      }
     
      client.proxy.exceptionCall(new Exception("hey")) must throwA(new ClientTimedOutException)
    }
     
    "log events" in {
      val conn = mock[Connection[TestRpcClient]]
      var events:List[ClientEvent] = Nil
      conn.client returns (new TestRpcClient {})
      conn.isHealthy returns true
      conn.unwrapException returns { case _ => TimeoutError }

      val client = new PooledClient[TestRpcClient] {
        val name = "test"
        def createConnection = conn
        override def handleEvent =  {
          case e => events ::= e
        }
      }
     
      Time.freeze()
      client.proxy.exceptionCall(new Exception("hey")) must throwA(new ClientTimedOutException)
      events must haveTheSameElementsAs(List(TimeoutEvent(Time.now)))
    }

    "not reuse an unhealthy node" in {
      val client = new TestClient

      client.proxy.call must be_==("client:0")
      client.numConnections must be_==(1)

      // This marks the connection bad, and so we must establish a new
      // one.
      client.proxy.exceptionCall(new Exception("wtf")) must throwA(new Exception("wtf"))

      client.proxy.call must be_==("client:1")
      client.numConnections must be_==(2)
    }
  }

  "failure accrual" should {
    "mark a connection unhealthy and recover it in within the specified parameters" in {
      val conn = spy(new TestConnection)
      var events: List[ClientEvent] = Nil
      conn.unwrapException returns { case _ => TimeoutError }

      val client = new PooledClient[TestRpcClient] {
        val name = "test"
        def createConnection = conn
        override def handleEvent =  {
          case e => events ::= e
        }
      }

      Time.freeze()
      client.proxy.exceptionCall(new Exception("wtf!")) must throwA(new ClientTimedOutException)
      for (_ <- 0 until client.maxAllowableFailures) {
        client.isHealthy must beTrue
        client.proxy.exceptionCall(new Exception("wtf!")) must throwA(new ClientTimedOutException)
      }

      client.isHealthy must beFalse

      Time.advance(client.retryInterval + 1.second)
      client.isHealthy must beTrue
      // And failure count is reset:
      client.proxy.exceptionCall(new Exception("wtf!")) must throwA(new ClientTimedOutException)
      client.isHealthy must beTrue
    }

    "be unaffected by ignored errors, but mark unhealthy on unknown exceptions" in {
      class IgnorableException extends Exception

      val conn = spy(new TestConnection)
      var events: List[ClientEvent] = Nil
      conn.unwrapException returns {
        case _: IgnorableException =>
          IgnoreError
        case e =>
          UnknownError
      }

      val client = new PooledClient[TestRpcClient] {
        val name = "test"
        def createConnection = conn
        override def handleEvent =  {
          case e => events ::= e
        }
      }

      Time.freeze()
      for (_ <- 0 until client.maxAllowableFailures * 2) {
        client.proxy.exceptionCall(new IgnorableException) must throwA(new IgnorableException)
        client.isHealthy must beTrue
        events must beEmpty
      }

      for (_ <- 0 until client.maxAllowableFailures + 1)
        client.proxy.exceptionCall(new Exception("hey")) must throwA(new Exception("hey"))

      client.isHealthy must beFalse
      events must be_==(List(UnhealthyEvent(Time.now)))
    }
  }
}
