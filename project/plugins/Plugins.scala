import sbt._


class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val scalaToolsReleases = "scala-tools.org" at "http://scala-tools.org/repo-releases/"
  val defaultProject = "com.twitter" % "standard-project" % "0.5.11"
}
