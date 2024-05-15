@Grab(group='org.codehaus.groovy', module='groovy-yaml', version='3.0.15')

import java.util.regex.Pattern
import java.util.regex.Matcher
import java.nio.file.Path
import groovy.yaml.YamlSlurper

import MergeMode
import SampleFilter

class Utils {

  /* Input Validation *************************************************/

  // TODO The nf-validation plugin may be able to supplant this
  static void preFlight(params, Boolean validate = true) {
    // NOTE This method updates params, passed as reference
    // Ensure all parameter values are serialisable

    // Show usage, if requested with --help
    if (params.help) { this.usage(params) }

    // Set reference parameters
    if (params.genome && params._ref.containsKey(params.genome)) {
      params.bowtie2 = params._ref[params.genome].bowtie2
    } else {
      this.usage(params, "No or invalid reference ID provided!")
    }

    // Return early, without validation, in certain contexts
    if (!validate) { return }

    // Check required parameters are set
    if (!params.fastq_dir) { this.usage(params, "Path to sample FASTQs must be provided!") }
    if (!params.amplicons) { this.usage(params, "Path to amplicons YAML must be provided!") }

    // Skip undetermined by default, unless --samples_process_undetermined is set
    if (params.samples_process_undetermined) { params._samples_skip_undetermined = false }

    // Construct sample filter
    params.samplesFilter = new SampleFilter(
      params.samples_pattern,
      params._samples_skip_undetermined,
      params.samples_include,
      params.samples_include_regex,
      params.samples_exclude,
      params.samples_exclude_regex
    )

    // Validate amplicon names
    if (!this.validAmpliconNames(params.amplicons, *params._internal.forbidden)) {
      this.usage(params, "Amplicon(s) detected with an invalid name; i.e., containing forbidden characters!")
    }

    // Validate merge mode
    try { params.merge_mode = MergeMode.from(params.merge_mode) }
    catch(Exception err) { this.usage(params, "${err.message}") }

    // Trim by default, unless --no_trimming is set
    if (params.no_trimming) { params._do_trimming = false }
  }

  static void usage(params, failure = null) {
    // Write all output to stderr
    def println = System.err.&println

    // Print usage information and exit
    println "\033[1;34mGENA Pipeline: Gene Editing NGS Analysis\033[0m"
    println ""
    println "Usage:"

    // Output all parameters for which help text exists
    params.each { k, v ->
      if (params._help.containsKey(k)) {
        println "  \033[0;37m--${k}\033[0m"
        println "  ${params._help[k]}"
        println "  [Value: ${v ?: "\033[2m<unset>\033[0m"}]"
        println ""
      }
    }

    println "Valid reference genome IDs:"

    // Output all reference IDs
    params._ref.each { k, v -> {
      File refPath = new File(v.bowtie2.dir)
      println "* ${k}${refPath.exists() ? "" : " \033[2m<not downloaded>\033[0m"}"
    }}

    println ""
    println "Valid filename patterns (case-insensitive):"
    PATTERN.findAll { pattern, _v -> !pattern.startsWith("_") }
           .each    { pattern, _v -> println "* ${pattern}" }

    // Output all merge modes
    println ""
    println "Valid paired sample merge strategies:"
    MergeMode.help()

    if (failure) {
      println ""
      println "\033[0;31m${failure}\033[0m"
      System.exit(1)
    } else {
      System.exit(0)
    }
  }

  private static Boolean validAmpliconNames(String ampliconsYaml, String... forbidden) {
    Object amplicons = new YamlSlurper().parse(ampliconsYaml as File);
    !amplicons.keySet().any { amplicon -> forbidden.any { amplicon.contains(it) } }
  }

  /* Sample Name Management *******************************************/

  static String REJECT = "_REJECT"

  // Mapping of sequencing platforms to their filename path patterns,
  // to extract the sample name. The sample name must be captured in a
  // group named "name".
  private static Map<String, Pattern> PATTERN = [
      ILLUMINA: Pattern.compile(/(?i)^(?<name>[\w-]+)_S\d+_(?:L\d+_)?R?\d(?:_\d+)?\.fastq/),

      // Used internally to identify rejected inputs
      _NEVER_MATCH: Pattern.compile(/(?!x)x/)
  ]

  static String getSampleName(Path sample, String platform) {
      Pattern pattern = PATTERN[platform.toUpperCase()] ?: PATTERN._NEVER_MATCH
      Matcher match = (sample.name =~ pattern)

      match ? match.group("name") : REJECT
  }
}
