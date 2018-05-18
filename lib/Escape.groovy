class Escape {
  // Class used as an indication that quoting should not be applied
  static class NoQuote {
    private String raw

    NoQuote(String raw) { this.raw = raw }
    String toString() { this.raw }
  }

  // Convenience , rather than having to do `new Escape.NoQuote(...)`
  static NoQuote noQuote(String raw) {
    new NoQuote(raw)
  }

  static String cmdAsPerlList(extCommand, Object... args) {
    // Serialises a command defined in the ext scope as a Perl list
    "(${this.cmdBuilder("\"", extCommand, args).join(", ")})"
  }

  static String cmdAsPerlString(extCommand, Object... args) {
    // Serialises a command defined in the ext scope as a Perl string
    "\"${this.cmdBuilder("\\\"", extCommand, args).join(" ")}\""
  }

  private static String[] cmdBuilder(String quote, extCommand, Object... args) {
    // NOTE args is of type List<Object>, so we have to explicitly
    // convert it into a Object[], so we can concatenate the arrays.
    Object[] cmd = extCommand.cmd + (args as ArrayList) + extCommand.extraArgs

    // TODO Do actual escaping!
    cmd.collect {
      if (it instanceof NoQuote) { "$it" }
      else { "${quote}${it}${quote}" }
    }
  }
}
