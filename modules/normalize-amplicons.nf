/*
 * TODO All that `normalize_amplicons.pl` does is to convert sequence
 * strings into uppercase. The input YAML file looks something like:
 *
 *   <NAME>:
 *     seq: <SEQUENCE>
 *     guide: <SEQUENCE>
 *     coding: <SEQUENCE>   # Optional
 *     HDR: <SEQUENCE>      # Optional
 *
 * Nonetheless, this ought to be straightforward to Nextflow-ify.
 */

// Nextflow wrapper to normalize_amplicons.pl
process normalizeAmplicons {
    input:
        // Amplicons YAML Path
        path ampliconsYaml

    output:
        // Normalised amplicons YAML
        path "${ampliconsYaml}.normalized"

    script:
        """
        normalize_amplicons.pl "$ampliconsYaml"
        """
}
