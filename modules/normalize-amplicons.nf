/* The input YAML file should look something like:
 *
 *   <NAME>:
 *     seq: <SEQUENCE>
 *     guide: <SEQUENCE>
 *     coding: <SEQUENCE>   # Optional
 *     HDR: <SEQUENCE>      # Optional
 *
 * Where <NAME> identifies each amplicon.
 *
 * Note that `guide`, `coding` and `HDR` can be any of:
 * - A single sequence string
 * - A comma-delimited string of sequences
 * - A list of single sequence strings
 *
 * The `guide` value may also be a list of dictionaries, each with a
 * `seq` key; i.e., that matches the normalised output (see below).
 *
 * Note that the `HDR` key is case-insensitive.
 *
 * The normalised output will then look like this:
 *
 *   <NAME>:
 *     seq: <UPPERCASE SEQUENCE>
 *     guide:
 *     - seq: <UPPERCASE SEQUENCE>
 *     # etc.
 *     coding:                      # Optional
 *     - <UPPERCASE SEQUENCE>
 *     # etc.
 *     HDR:                         # Optional
 *     - <UPPERCASE SEQUENCE>
 *     # etc.
 *
 * Note that unrecognised keys are left as-is.
 */

// Nextflow wrapper to normalize_amplicons.pl
process normalizeAmplicons {
    input:
        // State trigger to wait for deployment
        val isDeployed

        // Amplicons YAML Path
        path ampliconsYaml

    output:
        // Normalised amplicons YAML
        path "normalized.yaml"

    script:
        """
        normalize_amplicons.pl "$ampliconsYaml"
        """
}
