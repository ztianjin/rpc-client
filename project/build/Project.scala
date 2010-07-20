import sbt._
import com.twitter.sbt._

class RpcclientProject(info: ProjectInfo) extends StandardProject(info) with SubversionRepository {
  val specs     = "org.scala-tools.testing" % "specs"        % "1.6.2.1" % "test"
  val mockito   = "org.mockito"             % "mockito-all"  % "1.8.5" % "test"
  val vscaladoc = "org.scala-tools"         % "vscaladoc"    % "1.1-md-3"
  val xrayspecs = "com.twitter"             % "xrayspecs"    % "1.0.7"
  val pool      = "commons-pool"            % "commons-pool" % "1.5.4"
  val thrift    = "thrift"                  % "libthrift"    % "0.2.0"
  val ostrich   = "com.twitter"             % "ostrich"      % "1.1.14"
}
