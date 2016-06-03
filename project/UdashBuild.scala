import org.scalajs.sbtplugin.cross.CrossType
import sbt._

object UdashBuild extends Build {
  val CompileAndTest = "test->test;compile->compile"

  val publishedJS = taskKey[Attributed[File]]("JS file that gets packaged into JAR")
  val publishedJSDependencies = taskKey[File]("JS dependencies file that gets packaged into JAR")
}

case class IJFull(name: String = "shared") extends CrossType {
  def projectDir(crossBase: File, projectType: String): File =
    crossBase / projectType

  def sharedSrcDir(projectBase: File, conf: String): Option[File] =
    Some(projectBase.getParentFile / name / "src" / conf / "scala")
}