import sbt._
import com.twitter.sbt._

class RpcclientProject(info: ProjectInfo) extends StandardProject(info) with SubversionPublisher {

  val specs     = "org.scala-tools.testing" % "specs_2.8.0"  % "1.6.5" % "test"
  val mockito   = "org.mockito"             % "mockito-all"  % "1.8.5" % "test"

  val vscaladoc = "org.scala-tools" % "vscaladoc"    % "1.1-md-3"
  val util      = "com.twitter"     % "util"         % "1.6.4"
  val pool      = "commons-pool"    % "commons-pool" % "1.5.4"
  val thrift    = "thrift"          % "libthrift"    % "0.5.0"
  val ostrich   = "com.twitter"     % "ostrich"      % "2.3.6"

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>

  override def subversionRepository = Some("http://svn.local.twitter.com/maven-public")
}
