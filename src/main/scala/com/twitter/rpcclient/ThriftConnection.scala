// Copyright 2010 Twitter, Inc.
//
// A specialization of Client for thrift clients.

package com.twitter.rpcclient

import java.io.IOException
import scala.reflect.Manifest

import org.apache.thrift.protocol.{TProtocol, TBinaryProtocol}
import org.apache.thrift.transport.{
  TFramedTransport, TSocket, TTransportException, TTransport}
import org.apache.thrift.TException

import com.twitter.util.TimeConversions._
import com.twitter.util.Duration

class ThriftConnection[T](val host: String, val port: Int, framed: Boolean)
(implicit manifest: Manifest[T])
extends Connection[T]
{
  def SO_TIMEOUT     = 5.seconds
  val socket         = new TSocket(host, port, SO_TIMEOUT.inMilliseconds.toInt)
  val transport      = if (framed) new TFramedTransport(socket) else socket
  val protocol       = new TBinaryProtocol(transport)
  var didFailConnect = false
  val constructor    = manifest.erasure.getDeclaredConstructor(classOf[TProtocol])
  val client         = constructor.newInstance(protocol).asInstanceOf[T]

  def ensureOpen() {
    if (transport.isOpen)
      return

    try {
      transport.open()
    } catch {
      case _: TTransportException =>
        didFailConnect = true
    }
  }

  def teardown() {
    try {
      transport.close()
    } catch {
      case _: IOException | _: TException => /*ignore*/ ()
    }
  }

  // Flushing framed transports leaves it in some weird state.
  def flush() = ()

  override def unwrapException = {
    case _: IOException =>
      UnknownError
    case e: TTransportException if e.getType == TTransportException.TIMED_OUT =>
      TimeoutError
    case e: TTransportException
    if e.getCause.getClass == classOf[java.net.SocketTimeoutException] =>
      TimeoutError
  }

  override def isHealthy = !didFailConnect && super.isHealthy
}
