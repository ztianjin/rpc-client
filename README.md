# rpcclient

The `rpcclient` library encapsulates RPC communications. Specifically
it provides:

  - connection pooling
  - failure accrual management and health checking
  - a channel abstraction
  - logging and timing statistics (via ostrich)

We also provide convenient wrappers to fully encapsulate thrift
clients (though the underlying mechanism and its API is agnostic to
the RPC mechanism).

# Overview

`rpcclient` has two chief abstractions. The `Client`

    trait Client[T] {
      def proxy: T
      def isHealthy:Boolean
    }

represents an interface to an abstract client which proxies the
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

A full example using [thrift](http://incubator.apache.org/thrift/).
We are instantiate the client before starting the server we're
connecting to. Note that `is_healthy()` is a call that's defined in
the thrift interface for `MyClient`. `proxy` complies to
`MyClient.Iface`.

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

The above `ThriftClient` implements the following (and is provided
mostly as a convenience).

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

Use the `LoadBalancingChannel` to establish a round-robin channel to
multiple servers:

    val client = new LoadBalancingChannel(
      for (host <- hosts) yield new MyThriftClient(host, 9090, true, 10.seconds))

This is a `Client[MyThriftClient]` and like any other it has a `proxy`
member implementing the `MyClient.Iface` interface. Requests made
through it are dispatched in a round-robin manner to the given
(healthy subset of) clients.

# Health checking and exception handling

By default, `rpcclient` only handles connection/host health issues,
counting any RPC failure against that host. However, exceptions often
are used for application errors or control flow and should not be
taken to indicate a node failure. To modify the interpretation of
exceptions, define `unwrapException` in the `Connection` trait:

    override def unwrapException = super.unwrapException orElse {
      // ``InvalidQueryException''s are innocuous.
      case _:thrift.InvalidQueryException => rpcclient.IgnoreError
    }

Some applications also provide explicit health checking
facilities. `rpcclient` supports these with the `ApplicationHealth`
mixin for `Connection`. This mixin stubs a method
`isApplicationHealthy` returning a `Boolean` indicating healthyness of
the application. For example:

    def createConnection =
      new ThriftConnection[MyClient.Client](host, port, true/*framed*/)
      with ApplicationHealth[MyClient.Client] {
        override def SO_TIMEOUT = soTimeout
        val applicationCheckInterval = 10.seconds
   
        def isApplicationHealthy():Boolean =
          try {
            client.is_healthy()
          } catch {
            case _: Exception => false
          }
      }

will call `isApplicationHealthy` at most every 10 seconds (which in
turn asks the application over the RPC interface).

# Events

`rpcclient` generates events for 

- nodes becoming unhealthy
- nodes becoming healthy (after being unhealthy)
- nodes timing out

Subscribe to these by defining `handleEvent`, eg:

    class MyThriftClient extends PooledClient[MyClient.Iface] {
      ...

      override def handleEvent = {
        case rpcclient.UnhealthyEvent(time) =>
          log.warning("Index node %s became unhealthy at %s".format(hostport, time))
        case rpcclient.HealthyEvent(time, unhealthyTime) =>
          log.info("Index node %s became healthy after being unhealthy for %d seconds".format(
            hostport, (time - unhealthyTime).inSeconds))
        case rpcclient.TimeoutEvent(_) =>
          log.warning("Timeout for index node %s".format(hostport))
       }

# Timing and statistics

`rpcclient` maintains timings & counts for issued RPCs through the
[ostrich](http://github.com/robey/ostrich) library. Counts & timings
are maintained per RPC as well as per (RPC, host, port). Failure &
timeout counts are also maintained. eg.:

    rpcclient_index_hostport_localhost_4190_rpc_search: 
      (average=4, count=71, maximum=111, minimum=1, p25=2, p50=2, 
       p75=3, p90=6, p99=112, p999=112, p9999=112, standard_deviation=13)
    rpcclient_index_rpc_search: 
      (average=4, count=71, maximum=111, minimum=1, p25=2, p50=2, 
       p75=3, p90=6, p99=112, p999=112, p9999=112, standard_deviation=13)

Here 71 calls were made, all to `localhost:4190`. The average response
time was 4ms and the response time distribution is given (90% of
requests were satisfied within 6 milliseconds).

# Building

`rpcclient` uses [sbt](http://code.google.com/p/simple-build-tool/),
so in theory building is as simple as:

    $ sbt compile
