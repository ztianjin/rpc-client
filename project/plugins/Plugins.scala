import sbt._


class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val defaultProject = "com.twitter" % "standard-project" % "0.7.23"
  val twitter = "twitter" at "http://maven.twttr.com"
}
