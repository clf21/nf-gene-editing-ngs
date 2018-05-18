import java.util.regex.Pattern

import Utils

class SampleFilter {
    final String       filenamePattern   // Pattern to identify sample name and read from file names
    final Boolean      skipUndetermined  // Flag to skip files for undetermined samples
    final List<String> include           // List of sample names to include
    final Pattern      includeRegex      // Regular expression for sample names to include
    final List<String> exclude           // List of sample names to exclude
    final Pattern      excludeRegex      // Regular expression for sample names to exclude

    static private Pattern UNDETERMINED = Pattern.compile(/(?i)undetermined/)

    SampleFilter(
      String filenamePattern,
      Boolean skipUndetermined,
      String include, // Comma-separated
      String includeRegex,
      String exclude, // Comma-separated
      String excludeRegex
    ) {
        this.filenamePattern = filenamePattern

        this.skipUndetermined = skipUndetermined

        this.include = include ? include.split(",") : null
        this.includeRegex = includeRegex ? Pattern.compile(includeRegex) : null

        this.exclude = exclude ? exclude.split(",") : null
        this.excludeRegex = excludeRegex ? Pattern.compile(excludeRegex) : null
    }

    Boolean filter(String name, List<File> files) {
        // Check to see if the candidate (by name and its files) passes
        // the filter, given how the parameters are set
        //
        // NOTES:
        // * include/exclude override includeRegex/excludeRegex,
        //   respectively.
        // * include overrides exclude.

        // Discard anything already rejected upstream
        if (name == Utils.REJECT) {
            return false
        }

        if (this.skipUndetermined && name =~ this.UNDETERMINED) {
            return false
        }

        if (this.include) {
            Boolean match = this.include.find { it == name }
            if (!match) { return false }

        } else if (this.includeRegex) {
            Boolean match = name =~ this.includeRegex
            if (!match) { return false }

        } else if (this.exclude) {
            Boolean match = this.exclude.find { it == name }
            if (match) { return false }

        } else if (this.excludeRegex) {
            Boolean match = name =~ this.excludeRegex
            if (match) { return false }
        }

        true
    }
}
