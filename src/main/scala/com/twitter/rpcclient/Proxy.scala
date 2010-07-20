// Copyright 2010 Twitter, Inc.
//
// Some scala wrappers for Java proxies.

package com.twitter.rpcclient

import scala.reflect.Manifest
import java.lang.reflect

object Proxy {
  case class Invocation[T <: AnyRef](method: reflect.Method, args: Array[AnyRef]) {
    def name = method.getName

    @throws(classOf[Exception])
    def apply(obj:T) = try {
      method.invoke(obj, args:_*)       
    } catch {
      case e: reflect.InvocationTargetException =>
        throw e.getTargetException
      case e: reflect.UndeclaredThrowableException =>
        throw e.getUndeclaredThrowable
      case e =>
        throw e
    }

  }

  def apply[T <: AnyRef](cls: Class[_])(f: Invocation[T] => AnyRef): T = {
    val invocationHandler = new reflect.InvocationHandler() {
      def invoke(proxy: AnyRef, method: reflect.Method, args: Array[AnyRef]): AnyRef =
        f(Invocation(method, args))
    }

    val proxy = reflect.Proxy.newProxyInstance(cls.getClassLoader, Array(cls), invocationHandler)
    proxy.asInstanceOf[T]
  }
}
