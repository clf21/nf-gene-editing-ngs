@Grab(group='org.codehaus.groovy', module='groovy-yaml', version='3.0.16')

import java.nio.file.Path
import groovy.transform.AutoClone
import groovy.transform.AutoCloneStyle
import groovy.yaml.YamlBuilder
import groovy.yaml.YamlSlurper

// NOTE This is required for deep cloning
@AutoClone(style = AutoCloneStyle.SERIALIZATION)
class Metadata
  extends HashMap
  implements Comparable<Metadata> {

  /* Constructors *****************************************************/

  // Construct from sample name
  Metadata(String sampleName) {
    this.sampleName = sampleName
    this.sampleMetadata = [ amplicons: [], analyzed_amplicons: [] ]
  }

  // Construct from sample metadata YAML
  Metadata(Path sampleMetadataYaml) {
    Object sampleMetadata = new YamlSlurper().parse(sampleMetadataYaml)

    // NOTE Groovy forbids `this(sampleMetadata.name)` at anywhere other
    // than the first statement, so we have to be explicit about it here
    assert sampleMetadata.containsKey("name")
    this.sampleName = sampleMetadata.name

    // We have to materialise the `groovy.json.internal.LazyMap`, if we
    // have one from our YAML misadventures, into a bona fide `HashMap`
    // to allow deep cloning via serialisation
    this.sampleMetadata = (sampleMetadata?.metadata ?: [:]) as HashMap

    // Set `amplicons` to an empty list if it's not a list already
    if (this.sampleMetadata?.amplicons !instanceof List) {
      this.sampleMetadata.amplicons = []
    }

    // Set `analyzed_amplicons` to an empty list, overriding if it was
    // erroneously provided in the YAML
    this.sampleMetadata.analyzed_amplicons = []
  }

  /* Augmentation *****************************************************/

  // Add a read ID and return the augmented object
  Metadata cloneWithReadId(String readId) {
    assert !hasKeys(Keys.ReadId)
    assert readId =~ /^(?:Read[12]|Merged)$/

    Metadata cloned = this.clone()
    cloned.readId = readId

    cloned
  }

  // Add an amplicon name and return the augmented object
  Metadata cloneWithAmpliconName(String ampliconName) {
    assert !hasKeys(Keys.AmpliconName)

    Metadata cloned = this.clone()
    cloned.ampliconName = ampliconName

    cloned
  }

  /* Generated Values *************************************************/

  // Metadata ID (used for, e.g., process tagging)
  String id() {
    if (hasKeys(Keys.AnalysisId)) {
      return "${this.sampleName}_${this.readId}/${this.ampliconName}"

    } else if (hasKeys(Keys.SampleId)) {
      return "${this.sampleName}_${this.readId}"

    } else {
      assert hasKeys(Keys.SampleName)
      return this.sampleName
    }
  }

  // Publication directory for analyses
  String publishDir() {
    assert hasKeys(Keys.AnalysisId)

    String prefix =
      this.readId == "Merged" ? "${this.sampleName}"
                              : "${this.sampleName}_${this.readId}"

    "${prefix}/${this.ampliconName}"
  }

  // Sample metadata YAML
  String toSampleMetadataYaml() {
    assert hasKeys(Keys.SampleName)

    YamlBuilder yamlBuilder = new YamlBuilder()

    Map sampleMetadata = [ samples: [:] ]
    sampleMetadata.samples[this.sampleName] = this.sampleMetadata

    yamlBuilder sampleMetadata
    yamlBuilder.toString()
  }

  /* Distinguishers ***************************************************/

  // Known key collections
  static enum Keys {
    // Identifiers for "magic strings"
    SampleName("sampleName"),
    ReadId("readId"),
    AmpliconName("ampliconName"),

    // Standard known key collections
    SampleId(SampleName, ReadId),
    AnalysisId(SampleName, ReadId, AmpliconName)

    private Set keys

    Keys(String... keys) {
      this.keys = keys as Set
    }

    Keys(Keys... presets) {
      this(presets.sum { it.keys } as String[])
    }
  }

  Boolean hasKeys(String... keys) {
    keySet().containsAll(*keys)
  }

  Boolean hasKeys(Keys... presets) {
    hasKeys(presets.sum { it.keys } as String[])
  }

  /* Comparison *******************************************************/

  @Override
  int compareTo(Metadata that) {
    // Order by Sample Name, then Read ID, then Amplicon Name
    this.sampleName       <=> that.sampleName
    ?: this?.readId       <=> that?.readId
    ?: this?.ampliconName <=> that?.ampliconName
  }
}
