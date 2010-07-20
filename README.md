# rpcclient

The `rpcclient` library encapsulates RPC communications. Specifically
it provides:

  - connection pooling
  - failure detection and health checking
  - channel abstractions
  - logging and timing statistics (via ostrich)

It also provides convenient wrappers for fully encapsulating thrift
clients (though it is agnostic to the RPC mechanism).

# Overview

`rpcclient` has two chief abstractions. The `Client`

    trait Client[T] {
      def proxy: T
      def isHealthy:Boolean
    }

represents an interface to an abstract client and proxies the
underlying RPC stub. The `Connection`

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

deals with a concrete connection to a client. `rpcclient` maintains
pools of `Connection`s. The `Client` is implemented entirely by
`rpcclient`, but the user must provide the appropriate behavior in
`Connection`. A `Connection` implementation is provided for
[thrift](http://incubator.apache.org/thrift/).

# Usage

A full example using [thrift](http://incubator.apache.org/thrift/):

    import rpcclient.{Client, PooledClient, ThriftConnection, LoadBalancingChannel}

    class MyThriftClient(host: String, port: Int, framed: Boolean, soTimeout: Duration)
      extends PooledClient[MyClient.Iface]
    {
      val name = "myclient"

      def createConnection =
        new ThriftConnection[MyClient.Client](host, port, framed) {
          override def SO_TIMEOUT = soTimeout
        }
    }

Instantiating `MyThriftClient` now proxies `MyClient.Iface` through
`proxy`:

    val client = new MyThriftClient("localhost", 9090, true/*use framed transport*/, 10.seconds)
    val result = client.proxy.someCall(x, y, z)

Use the `LoadBalancingChannel` to establish a round-robin channel to
multiple servers:

    val client = new LoadBalancingChannel(
      for (host <- hosts) yield new MyThriftClient(host, 9090, true, 10.seconds))

This in turn has its own `proxy` member that dispatches the request to
a round-robin client.

The above behavior is encapsulated fully in `ThriftClient`, and is
equivalent the following. Here, we are instantiating the client before
we started the server for the interface. Note that `is_healthy()` is a
call that's defined in the thrift interface for `MyClient`.

    scala> val client = new ThriftClient[MyClient.Iface, MyClient.Client]("localhost", 4190)
    client: com.twitter.rpcclient.ThriftClient[MyClient.Iface,MyClient.Client] = $anon$1@784425c

    scala> client.proxy.is_healthy()
    com.twitter.rpcclient.ClientUnavailableException
        at com.twitter.rpcclient.PooledClient$$anonfun$1.apply(Client.scala:169)
        at com.twitter.rpcclient.PooledClient$$anonfun$1.apply(Client.scala:166)
        at com.twitter.rpcclient.Proxy$$anon$1.invoke(Proxy.scala:31)
        at $Proxy0.toString(Unknown Source)
        at scala.runtime.ScalaRunTime$.stringOf(ScalaRunTime.scala:165)
        at RequestResult$.<init>...

    scala> // Whoops. Start the server
    scala> client.proxy.is_healthy()
    res6: Boolean = true

# Health checking and exception handling

By default, any exception thrown during a call is interepreted as
indicative of an unhealthy server (that is, it is counted as a
failure). However, exceptions can sometimes have different meanings:
for example exceptions sometimes indicate an application error and
should not be taken to indicate a node failure. To modify the
interpretation of exceptions, define `unwrapException` in the
`Connection` trait:

    override def unwrapException = super.unwrapException orElse {
      // ``InvalidQueryException''s are innocuous.
      case _:thrift.InvalidQueryException => rpcclient.IgnoreError
    }

# Building

`rpcclient` uses [sbt](http://code.google.com/p/simple-build-tool/),
so in theory building is as simple as:

    $ sbt compile
