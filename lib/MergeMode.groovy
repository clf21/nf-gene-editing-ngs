enum MergeMode {
  Auto(
    "auto",
    "Merge paired-end reads, process the merged reads and remaining unmerged reads"
  ),

  Merge(
    "merge",
    "Merge paired-end reads, process the merged reads but drop the unmerged reads"
  ),

  Both(
    "both",
    "Merge paired-end reads and process the merged reads; also process the original reads through the single-end read pipeline"
  ),

  NoMerge(
    "no-merge",
    "Don't merge the paired-end reads and instead process them as single-end reads"
  )

  private final String mode
  private final String helpText

  private static final lookup = MergeMode.values()
                                         .collectEntries { [ it.mode, it ] }
                                         .asImmutable()

  // Longest mode length, for pretty-printing
  private static final width = MergeMode.values()
                                        .collect { it.mode.size() }
                                        .max()

  MergeMode(String mode, String helpText) {
    this.mode = mode
    this.helpText = helpText
  }

  String toString() {
    this.mode
  }

  static MergeMode from(String mode) {
    if (!lookup.containsKey(mode)) {
      throw new MissingPropertyException("Unrecognised merge strategy '${mode}'!")
    }

    lookup.get(mode)
  }

  static void help() {
    this.values()
        .collectEntries { [ it.mode, it.helpText ] }
        .each { mode, help -> println "* ${mode.padRight(width)}   ${help}" }
  }
}
