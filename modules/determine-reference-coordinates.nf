// Nextflow wrapper to determine_reference_coordinates.pl
process determineReferenceCoordinates {
    input:
        // Alignments YAML Path
        // With the following structure:
        // * Structure, keyed by alignment name:
        //   * CIGAR  String
        //   * start  String
        path alignmentsYaml

    output:
        // Association between amplicon name and alignment coordinates
        path "coordinates.yaml"

    script:
        """
        determine_reference_coordinates.pl "$alignmentsYaml"
        """
}
