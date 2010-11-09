// Copyright 2010 Twitter, Inc.
//
// An easy to use thrift client interface.

package com.twitter.rpcclient

import scala.reflect.Manifest

import org.apache.thrift.protocol.TProtocol

import com.twitter.util.TimeConversions._
import com.twitter.util.Duration

class ThriftClient[Intf <: AnyRef, Cli <: Intf]
(host: String, port: Int, framed: Boolean, soTimeout: Duration, val name: String)
(implicit manifestIntf: Manifest[Intf], manifestCli: Manifest[Cli])
extends PooledClient[Intf]()(manifestIntf)
{
  def this(host: String, port: Int)
    (implicit manifestIntf: Manifest[Intf], manifestCli: Manifest[Cli]) =
      this(host, port, true, 5.seconds, manifestIntf.erasure.getName)

  def createConnection =
    new ThriftConnection[Cli](host, port, framed) {
      override def SO_TIMEOUT = soTimeout
    }
}
