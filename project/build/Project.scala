import sbt._
import com.twitter.sbt._

class RpcclientProject(info: ProjectInfo) extends StandardProject(info) with SubversionPublisher {
  override def subversionRepository = Some("http://svn.local.twitter.com/maven")

  val twitterMavenRepo = "twitter.com" at "http://maven.twttr.com"
  val specs     = buildScalaVersion match {
    case "2.7.7" => "org.scala-tools.testing" % "specs" % "1.6.2.1" % "test"
    case _ => "org.scala-tools.testing" %% "specs" % "1.6.5" % "test"
  }
  val mockito   = "org.mockito"             % "mockito-all"  % "1.8.5" % "test"
  val vscaladoc = "org.scala-tools"         % "vscaladoc"    % "1.1-md-3"
  val util = buildScalaVersion match {
    case "2.7.7" => "com.twitter" % "util" % "1.1.2"
    case _ => "com.twitter" % "util" % "1.2.8"
  }
  val pool      = "commons-pool"            % "commons-pool" % "1.5.4"
  val thrift    = "thrift"                  % "libthrift"    % "0.5.0"
  val ostrich = buildScalaVersion match {
    case "2.7.7" => "com.twitter" % "ostrich" % "1.2.10"
    case _ => "com.twitter" %% "ostrich" % "2.2.6"
  }

  override def disableCrossPaths = false
  override def managedStyle = ManagedStyle.Maven
}
