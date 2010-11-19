import sbt._
import com.twitter.sbt._

class RpcclientProject(info: ProjectInfo) extends StandardProject(info) with SubversionPublisher {
  val twitterMavenRepo = "twitter.com" at "http://maven.twttr.com"
  val specs     = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5"
  val mockito   = "org.mockito"             % "mockito-all"  % "1.8.5" % "test"
  val vscaladoc = "org.scala-tools"         % "vscaladoc"    % "1.1-md-3"
  val xrayspecs = "com.twitter"             % "xrayspecs_2.8.0" % "2.0"
  val pool      = "commons-pool"            % "commons-pool" % "1.5.4"
  val thrift    = "org.apache.thrift"       % "libthrift"    % "0.5.0"
  val ostrich   = "com.twitter"             % "ostrich" % "2.3.0"

  override def disableCrossPaths = true
  override def managedStyle = ManagedStyle.Maven
}
