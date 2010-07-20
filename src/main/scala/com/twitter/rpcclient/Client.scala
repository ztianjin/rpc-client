// Copyright 2010 Twitter, Inc.
//
// Encapsulate communication to a single RPC endpoint.
//
// The "client" is the abstract endpoint, a "connection" is a concrete
// connection to said client.

package com.twitter.rpcclient

import scala.reflect.Manifest

import java.util.concurrent.atomic.AtomicInteger

import org.apache.commons.pool.PoolableObjectFactory
import org.apache.commons.pool.impl.StackObjectPool

import com.twitter.ostrich.Stats
import com.twitter.xrayspecs.{Time, Duration}
import com.twitter.xrayspecs.TimeConversions._

// Events that occur in individual clients. They may be observed.
sealed abstract class ClientEvent
case class UnhealthyEvent(timestamp: Time)                    extends ClientEvent
case class HealthyEvent(timestamp: Time, unhealthyTime: Time) extends ClientEvent
case class TimeoutEvent(timestamp: Time)                      extends ClientEvent

// Translated errors - used when unwrapping exceptions.
sealed abstract class ClientError
object TimeoutError extends ClientError
object IgnoreError  extends ClientError
object UnknownError extends ClientError

// We based our exceptions on RuntimeException because they are
// unchecked, and so we avoid Java runtime errors when passing them
// through the proxy.
class ClientException            extends RuntimeException
class ClientUnavailableException extends ClientException
class ClientTimedOutException    extends ClientException

// The client wraps an underlying proxy stub, abstracting away
// connection management. It also adorns it with some useful
// methods. Access the underlying client interface through ``proxy''.
trait Client[T] {
  def proxy: T
  def isHealthy:Boolean
}

// ``Connection[T]'' describes an individual connection to a client of
// type `T'. This wraps the RPC client itself, and adorns it with the
// ability to check connection health, client health, and etc.
trait Connection[+T] {
  val client: T
  val host: String
  val port: Int

  // Ensure that the underlying connection is open.  This is always
  // called at least once prior to use.
  def ensureOpen(): Unit
  // Tear down the underlying connection.  Called before relinquishing
  // this node from the pool.
  def teardown(): Unit
  // Flush is called every time the node is given back to the pool.
  def flush(): Unit

  // Interpret an exception that occured during use.
  def unwrapException: PartialFunction[Exception, ClientError] = {
    case _ => UnknownError
  }

  // Failure accrual management.

  // Defines whether this node is considered healthy (eligible for
  // use).
  var didFail = false
  def isHealthy: Boolean = !didFail
  def markFailed() { didFail = true }
}

abstract class PooledClient[T <: AnyRef](implicit manifest: Manifest[T]) extends Client[T] {
  // Minimal complete definition: createConnection
  def createConnection: Connection[T]
  def handleEvent: PartialFunction[ClientEvent, Unit] = { case _ => () }
  val name: String

  // Additional parameters for health checks:
  val maxAllowableFailures: Int = 5
  val retryInterval: Duration = 10.seconds

  // Pool stats.
  def numActive = pool.getNumActive
  def numIdle   = pool.getNumIdle
  def poolSize  = numActive + numIdle

  class ConnectionFactory extends PoolableObjectFactory {
    def makeObject: Object = {
      val c = createConnection
      c.ensureOpen()
      c
    }

    def validateObject (o: Object) = o.asInstanceOf[Connection[T]].isHealthy
    def destroyObject  (o: Object) = o.asInstanceOf[Connection[T]].teardown()
    def activateObject (o: Object) = o.asInstanceOf[Connection[T]].ensureOpen()
    def passivateObject(o: Object) = o.asInstanceOf[Connection[T]].flush()
  }

  private val pool = new StackObjectPool(new ConnectionFactory)
  @volatile var wentUnhealthyAt:Option[Time] = None
  val numFailures = new AtomicInteger(0)
  def isHealthy = wentUnhealthyAt match {
    case None =>
      true
    case Some(time) if time < retryInterval.ago =>
      markHealthy()
      true
    case _ =>
      false
  }

  def didSucceed() {
    markHealthy()
  }

  def didFail() {
    if (numFailures.incrementAndGet > maxAllowableFailures)
      markUnhealthy()
  }

  def markUnhealthy() {
    wentUnhealthyAt = Some(Time.now)
    for (now <- wentUnhealthyAt)
      logEvent(UnhealthyEvent(now))
  }

  def markHealthy() {
    for (unhealthyTime <- wentUnhealthyAt)
      logEvent(HealthyEvent(Time.now, unhealthyTime))

    wentUnhealthyAt = None
    numFailures.set(0)
  }

  def get:Option[Connection[T]] =
    if (isHealthy) {
      try {
        Some(pool.borrowObject().asInstanceOf[Connection[T]])
      } catch {
        case _: NoSuchElementException =>
          didFail()
          None
      }
    } else {
      None
    }

  def put(conn: Connection[T]) {
    if (conn.didFail || !conn.isHealthy) {
      // it's useless to return it to the pool at this point.
      didFail()
    } else {
      pool.returnObject(conn)
      didSucceed()
    }
  }

  val _proxy = Proxy[T](manifest.erasure) { invocation =>
    val conn = get match {
      case Some(conn) => conn
      case None => throw new ClientUnavailableException
    }

    try {
      val (rv, msec) = Stats.duration(invocation(conn.client))
      Stats.addTiming("rpcclient_%s_rpc_%s".format(name, invocation.name), msec.toInt)
      Stats.addTiming(
        "rpcclient_%s_hostport_%s_%d_rpc_%s".format(name, conn.host, conn.port, invocation.name),
        msec.toInt)
      rv
    } catch {
      case e: Exception =>
        val unwrappedException =
          if (conn.unwrapException.isDefinedAt(e))
            conn.unwrapException(e)
          else
            UnknownError

        unwrappedException match {
          case IgnoreError => ()  // does not affect health accounting
          case TimeoutError =>
            Stats.incr("rpcclient_%s_%s_timeout".format(name, invocation.name))
            logEvent(TimeoutEvent(Time.now))
            conn.markFailed()
            throw new ClientTimedOutException
          case UnknownError =>
            Stats.incr("rpcclient_%s_%s_exception".format(name, invocation.name))
            conn.markFailed()
        }

        throw e
    } finally {
      put(conn)
    }
  }

  def proxy = _proxy

  def logEvent(e: ClientEvent) {
    if (handleEvent.isDefinedAt(e))
      handleEvent(e)
  }
}
