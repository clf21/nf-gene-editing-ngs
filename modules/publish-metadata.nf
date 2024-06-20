@Grab(group='org.codehaus.groovy', module='groovy-yaml', version='3.0.16')
import groovy.yaml.YamlBuilder

process publishMetadata {
    publishDir "${params.outdir}", mode: "copy", overwrite: true

    input:
        // All sample metadata (concatenated) YAML file
        path sampleMetadata

        // Experiment metadata YAML file
        path experimentMetadata

        // Prepared amplicons YAML file
        path preparedAmplicons

    output:
        path "metadata.yaml"
        path "params.yaml"
        path "amplicons.yaml"

    shell:
        YamlBuilder yamlBuilder = new YamlBuilder()

        // We only want to export the parameters for which there is help
        // text (i.e., exposed to the user); all others are considered
        // internal. We also ignore parameters with a `null` value.
        yamlBuilder params.findAll { k, v -> k in params._help && v != null }
        paramsYaml = yamlBuilder.toString().trim()

        // Template tags:
        // * experimentMetadata  Experiment metadata YAML file
        // * paramsYaml          Serialised parameters (i.e., YAML string)
        // * preparedAmplicons   Prepared amplicons YAML file
        // * sampleMetadata      Sample metadata (concatenated) YAML file
        template "publish_metadata.pl"
}

workflow toSampleMetadata {
    take:
        // NOTE To generate the sample metadata, this workflow needs
        // channels of Metadata objects, which are then serialised to
        // YAML and concatenated into a single YAML file for downstream
        // merging. The trick is, when converted to YAML, the metadata
        // is keyed by sample name and, where different sources of
        // Metadata objects contain different augmentations, merging
        // brings everything altogether.
        //
        // For convenience sake, this workflow specifically takes the
        // Metadata generated from sample selection and those which have
        // passed the allele analysis -- i.e., representing the "source"
        // metadata and the only current augmentation, respectively. In
        // future, should other sources of augmentation appear, this
        // workflow would need to be updated appropriately, as described
        // here.

        // Channels of single and paired-end samples. That is, tuples of
        // the form:
        // * Metadata<Sample name>
        // * Sample FASTQs
        //
        // NOTE This is the output from listSequencingSamples
        singleSamples
        pairedSamples

        // Channel of successful analyses. That is, tuples of the form:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * CRISPResso analysis
        //
        // NOTE This is the output from identifyAlleles
        passed

    main:
        // Normalise samples
        singleSamples
        | mix(pairedSamples)
        | map { meta, _fastqs -> meta.clone() }
        | set { allSamples }

        // Normalise successful analyses
        passed
        | map { meta, _analysis -> meta.clone() }
        | set { passedSamples }

        // Mix all samples with passed samples, convert each to its
        // sample metadata YAML form, then concatenate for downstream.
        allSamples
        | mix(passedSamples)
        | map { meta -> meta.toSampleMetadataYaml() }
        | collectFile(name: "all-sample-metadata.yaml")
        | set { sampleMetadata }

    emit:
        sampleMetadata
}
