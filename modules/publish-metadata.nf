@Grab(group='org.codehaus.groovy', module='groovy-yaml', version='3.0.16')
import groovy.yaml.YamlBuilder

process _publishMetadata {
    publishDir "${params.outdir}", mode: "copy", overwrite: true

    input:
        path preparedAmplicons

    output:
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
        // * paramsYaml         Serialised parameters (i.e., YAML string)
        // * preparedAmplicons  Prepared amplicons YAML file
        template "publish_metadata.sh"
}

workflow publishMetadata {
  take:
    // Prepared amplicons YAML file
    preparedAmplicons

  main:
    _publishMetadata(
        preparedAmplicons
    )
}
