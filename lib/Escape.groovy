class Escape {
  // Class used as an indication that quoting should not be applied
  static class NoQuote {
    private String raw

    NoQuote(String raw) { this.raw = raw }
    String toString() { this.raw }
  }

  // Convenience, rather than having to do `new Escape.NoQuote(...)`
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

  private static List cmdBuilder(String quote, extCommand, Object... args) {
    List cmd =
      extCommand.cmd +
      // `args` needs to be cast to the right type for concatenation
      (args as List) +
      // `extCommand.extraArgs` is a string, so is tokenised
      shellTokenise(extCommand.extraArgs)

    // TODO Do actual escaping!
    cmd.collect {
      if (it instanceof NoQuote) { "$it" }
      else { "${quote}${it}${quote}" }
    }
  }

  private static List shellTokenise(String s) {
    // Groovy port of https://gist.github.com/raymyers/8077031
    List tokens = []

    // Tokeniser state
    Boolean inEscape = false
    Boolean inQuote = false
    Character quote = ' '
    Integer lastCloseQuoteIndex = Integer.MIN_VALUE

    // Current token
    StringBuilder token = new StringBuilder()

    s.eachWithIndex { String c, Integer i ->
      if (inEscape) {
        token.append(c)
        inEscape = false

      } else if (c == '\\' && !(inQuote && quote == '\'')) {
        inEscape = true

      } else if (inQuote && c == quote) {
        inQuote = false
        lastCloseQuoteIndex = i

      } else if (!inQuote && (c == '\'' || c == '"')) {
        inQuote = true
        quote = c as Character

      } else if (!inQuote && c.isAllWhitespace()) {
        if (token || lastCloseQuoteIndex == i - 1) {
          tokens.add(token.toString())
          token = new StringBuilder()
        }

      } else {
        token.append(c)
      }
    }

    // Append any remainder
    if (token || lastCloseQuoteIndex == s.size() - 1) {
      tokens.add(token.toString())
    }

    tokens
  }
}
