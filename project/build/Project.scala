import sbt._
import com.twitter.sbt._

class RpcclientProject(info: ProjectInfo) extends StandardProject(info) with SubversionRepository {
  val specs     = buildScalaVersion match {
    case "2.7.7" => "org.scala-tools.testing" % "specs" % "1.6.2.1"
    case _ => "org.scala-tools.testing" %% "specs" % "1.6.5"
  }
  val mockito   = "org.mockito"             % "mockito-all"  % "1.8.5" % "test"
  val vscaladoc = "org.scala-tools"         % "vscaladoc"    % "1.1-md-3"
  val xrayspecs = buildScalaVersion match {
    case "2.7.7" => "com.twitter" % "xrayspecs" % "1.0.7"
    case _ => "com.twitter" %% "xrayspecs" % "2.0"
  }
  val pool      = "commons-pool"            % "commons-pool" % "1.5.4"
  val thrift    = "thrift"                  % "libthrift"    % "0.2.0"
  val ostrich = buildScalaVersion match {
    case "2.7.7" => "com.twitter" % "ostrich" % "1.1.14"
    case _ => "com.twitter" %% "ostrich" % "2.2.6"
  }

  override def disableCrossPaths = false
}
