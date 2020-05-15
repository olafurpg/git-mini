package gitflip

import metaconfig.cli.CliApp
import metaconfig.cli.Command
import GitFlipEnrichments._
import java.io.PrintWriter
import java.nio.file.Path
import scala.util.control.NonFatal

object AmendCommand extends Command[AmendOptions]("amend") {
  def run(value: Value, app: CliApp): Int = {
    SwitchCommand.withMinirepo("amend", value.minirepo, app) { name =>
      Option(System.getenv("EDITOR")) match {
        case None =>
          app.error(
            "can't amend since the environment variable $EDITOR is not defined.\n\t" +
              s"To fix this problem run: EDITOR=vim ${app.arguments.mkString(" ")}"
          )
          1
        case Some(editor) =>
          editFile(editor, app.exclude(name), app)
      }
    }
  }

  private def editFile(editor: String, tmp: Path, app: CliApp): Int = {
    try {
      // Adjusted from https://stackoverflow.com/questions/29733038/running-interactive-shell-program-in-java
      val proc = Runtime.getRuntime().exec("/bin/bash")
      val stdin = proc.getOutputStream()
      val pw = new PrintWriter(stdin)
      pw.println(s"$editor $tmp < /dev/tty > /dev/tty")
      pw.close()
      proc.waitFor()
    } catch {
      case NonFatal(e) =>
        app.error(s"failed to edit file with EDITOR='$editor'")
        e.printStackTrace(app.out)
        1
    }
  }
}
