package gitflip

import metaconfig.cli.Command
import metaconfig.cli.CliApp
import java.nio.file.Files
import gitflip.GitFlipEnrichments._
import scala.jdk.CollectionConverters._
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import org.typelevel.paiges.Doc
import scala.collection.mutable

object CreateCommand extends Command[CreateOptions]("create") {
  override def description: Doc = Doc.text("Create new git minirepo")
  def run(value: Value, app: CliApp): Int = {
    if (value.directories.isEmpty) {
      app.error(
        s"can't create a new minirepo from an empty list of directories to exclude. " +
          s"To fix this problem, pass the directory that you wish to exclude. For example:\n\t" +
          s"${app.binaryName} create my-directory"
      )
      1
    } else {
      value.name match {
        case None =>
          app.error(
            "missing --name. To fix this problem, provide a name for the new minirepo:\n\t" +
              s"${app.binaryName} create --name MINIREPO_NAME ${value.directories.mkString(" ")}"
          )
          1
        case Some(name) =>
          val minirepo = app.minirepo(name)
          if (Files.isDirectory(minirepo)) {
            app.error(
              s"can't create minirepo '$name' because it already exists.\n\t" +
                s"To amend this minirepo run: ${app.binaryName} amend $name"
            )
            1
          } else {
            val installExit =
              InstallCommand.run((), app, isInstallCommand = false)
            if (installExit != 0) {
              installExit
            } else {
              require(Files.isRegularFile(app.git), "git-flip is not installed")
              val backup = Files.createTempFile("git-flip", ".git")
              Files.move(app.git, backup, StandardCopyOption.REPLACE_EXISTING)
              if (
                app.exec(
                  "git",
                  "init",
                  "--separate-git-dir",
                  minirepo.toString()
                ) == 0
              ) {
                writeInclude(value, app, name)
                writeExclude(app, name)
                app.exec(List("git", "add", ".")).ifSuccessful {
                  app.exec(
                    "git",
                    "commit",
                    "-m",
                    s"First commit in minirepo $name"
                  )
                }
              } else {
                Files.move(backup, app.git)
                app.error(s"Failed to create new minirepo named '$name'")
                1
              }
            }
          }
      }
    }
  }
  def writeInclude(value: Value, app: CliApp, name: String): Unit = {
    val includes = app.includes(name)
    val includeDirectories =
      value.directories.map(dir => app.toAbsolutePath(dir).toString())
    Files.write(
      includes,
      includeDirectories.asJava,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
  }
  def writeExclude(app: CliApp, name: String): Unit = {
    val includes = app.readIncludes(name)
    val excludes = mutable.ListBuffer.empty[String]
    val toplevel = app.toplevel
    excludes += "/*"
    includes.foreach { include =>
      val relativePath = toplevel.relativize(include)
      excludes += s"!/$relativePath"
    }
    Files.write(
      app.exclude(name),
      excludes.asJava,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
  }
}
