import java.nio.file.Path

class RelativeDir {
  // Compute the path of `actualPath` relative to `workingPath`
  static String to(String workingPath, String actualPath) {
    Path working = (new File(workingPath)).toPath()
    Path actual = (new File(actualPath)).toPath()

    working.relativize(actual).toString()
  }
}
