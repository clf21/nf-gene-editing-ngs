@Grab(group='org.codehaus.groovy', module='groovy-yaml', version='3.0.16')

import java.util.regex.Pattern
import java.util.regex.Matcher
import java.nio.file.Path
import groovy.yaml.YamlSlurper
import nextflow.Nextflow

import MergeMode
import SampleFilter

class Utils {

  /* Input Validation *************************************************/

  // TODO The nf-validation plugin may be able to supplant this
  static void preFlight(workflow, params) {
    // NOTE This method updates params, passed as reference
    // Ensure all parameter values are serialisable

    // Show usage, if requested with --help
    if (params.help) { usage(workflow, params) }

    // Set reference parameters
    if (params.genome && params._ref.containsKey(params.genome)) {
      params.bowtie2 = params._ref[params.genome].bowtie2
    } else {
      usage(workflow, params, "No or invalid reference ID provided!")
    }

    // Check required parameters are set
    if (!params.fastq_dir) { usage(workflow, params, "Path to sample FASTQs must be provided!") }
    if (!params.amplicons) { usage(workflow, params, "Path to amplicons YAML must be provided!") }

    // Validate aligner profile
    if (!params._aligner.containsKey(params.aligner_profile)) {
      usage(workflow, params, "The '${params.aligner_profile}' aligner profile is not defined!")
    }

    if (
      (params.aligner_profile == "custom" && !(params.aligner_custom_read_args || params.aligner_custom_amplicon_args)) ||
      (params.aligner_profile != "custom" && (params.aligner_custom_read_args || params.aligner_custom_amplicon_args))
    ) {
      usage(workflow, params, "The 'custom' aligner profile must be used with --aligner_custom_read_args and/or --aligner_custom_amplicon_args!")
    }

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
    if (!validNames(getAmpliconNames(params.amplicons), *params._internal.forbidden)) {
      usage(workflow, params, "Amplicon(s) detected with an invalid name; i.e., containing forbidden characters!")
    }

    // Validate sample names in metadata (if any)
    // NOTE The metadata makes references to amplicons and samples.
    // These are not validated for consistency (but they could be...)
    if (params.metadata && !validNames(getSampleNamesFromMetadata(params.metadata), *params._internal.forbidden)) {
      usage(workflow, params, "Sample(s) in metadata detected with an invalid name; i.e., containing forbidden characters!")
    }

    // Validate merge mode
    try { params.merge_mode = MergeMode.from(params.merge_mode) }
    catch(Exception err) { usage(workflow, params, "${err.message}") }

    // Trim by default, unless --no_trimming is set
    if (params.no_trimming) { params._do_trimming = false }
  }

  static void usage(workflow, params, failure = null) {
    // Write all output to stderr
    def println = System.err.&println

    // Print usage information and exit
    println "\033[1;34m${workflow.manifest.description}\033[0m"
    println "\033[0;34m${workflow.manifest.name} ${version(workflow)}\033[0m"
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

    // Output all aligner profiles
    println ""
    println "Valid aligner profiles:"
    int _bowtie2_align = params._aligner.keySet()
                                        .collect { it.size() }
                                        .max()

    params._aligner.each { name, profile -> {
      println "* ${name.padRight(_bowtie2_align)}   ${profile._help}"
    }}

    if (failure) {
      println ""
      println "\033[0;31m${failure}\033[0m"
      System.exit(1)
    } else {
      System.exit(0)
    }
  }

  // Generate workflow version string (taken from the nf-core template)
  private static String version(workflow) {
      String version_string = ""
      if (workflow.manifest.version) {
          def prefix_v = workflow.manifest.version[0] != 'v' ? 'v' : ''
          version_string += "${prefix_v}${workflow.manifest.version}"
      }

      if (workflow.commitId) {
          def git_shortsha = workflow.commitId.substring(0, 7)
          version_string += "-g${git_shortsha}"
      }

      return version_string
  }

  private static List<String> getAmpliconNames(String ampliconsYaml) {
    Object amplicons = new YamlSlurper().parse(Nextflow.file(ampliconsYaml));
    amplicons.keySet() as List
  }

  private static List<String> getSampleNamesFromMetadata(String metadataYaml) {
    Object metadata = new YamlSlurper().parse(Nextflow.file(metadataYaml));
    (metadata.samples ?: [:]).keySet() as List
  }

  private static Boolean validNames(List<String> names, String... forbidden) {
    !names.any { name -> forbidden.any { name.contains(it) } }
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
