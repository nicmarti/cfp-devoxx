package library
import org.apache.commons.io.IOUtils
import java.io.File
import scala.collection.JavaConversions._

case class GitInfo(version:String, branch:String)

object GitUtils{
  def getGitVersion:GitInfo={
    try {
      val version = execCmd("git log -1").headOption.getOrElse("Unknown").replace("commit", "").trim
      val branch :String = execCmd("git branch").filter(s=>s.contains("*")).headOption.getOrElse("No current branch").replace("*", "").trim
      GitInfo(version, branch)
    } catch {
      case _:Exception => GitInfo("Unknown", "Unknown")
    }
  }
  
  private def execCmd(extractedLocalValue: java.lang.String): List[String] = {
    val process = Runtime.getRuntime.exec(extractedLocalValue, null, new File("."))
    process.waitFor()
    IOUtils.readLines(process.getInputStream).toList
  }
}
