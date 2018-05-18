import java.nio.file.Path

import Utils

class Metadata
  extends HashMap
  implements Comparable<Metadata> {

  /* Constructors *****************************************************/

  // Construct from sample name
  Metadata(String sampleName) {
    this.sampleName = sampleName

    // ID: ${sampleName}"
    this.id = sampleName
  }

  // Construct from sample file and platform identifier
  Metadata(Path sampleFile, String platform) {
    this(Utils.getSampleName(sampleFile, platform))
  }

  // Construct from sample name and read ID
  Metadata(String sampleName, String readId) {
    this(sampleName)

    assert readId =~ /^(?:Read[12]|Merged)$/
    this.readId = readId

    // ID: ${sampleName}_${readId}
    this.id += "_${readId}"
  }

  // Construct from sample name, read ID and amplicon name strings
  Metadata(String sampleName, String readId, String ampliconName) {
    this(sampleName, readId)
    this.ampliconName = ampliconName

    // ID: ${sampleName}_${readId}/${ampliconName}
    this.id += "/${ampliconName}"

    // Publication directory
    String prefix = this.readId == "Merged" ? "${this.sampleName}"
                                            : "${this.sampleName}_${this.readId}"

    this.publishDir = "${prefix}/${ampliconName}"
  }

  // Convenience constructor if given an amplicon YAML file
  Metadata(String sampleName, String readId, Path ampliconYaml) {
    // The amplicon name is the same as the filename, with the YAML
    // extension stripped from the end
    this(sampleName, readId, ampliconYaml.name - ~/\.ya?ml$/)
  }

  // TODO Construct from arbitrary maps

  /* Augmentation *****************************************************/

  // The left shift operator is used as an idiomatic way to augment
  // the metadata with additional information. The operator is
  // overloaded to perform this based on the type of the RHS.
  // TODO Make this less rigid

  // SampleName -> SampleId
  // e.g.: new Metadata("foo") << "Read1" == new Metadata("foo", "Read1")
  Metadata leftShift(String readId) {
    assert hasKeys(Keys.SampleName) && !hasKeys(Keys.ReadId)
    new Metadata(this.sampleName, readId)
  }

  // SampleId -> AnalysisId
  // e.g.: new Metadata("foo", "Read2") << ampliconYaml == new Metadata("foo", "Read2", ampliconYaml)
  Metadata leftShift(Path ampliconYaml) {
    assert hasKeys(Keys.SampleId) && !hasKeys(Keys.AmpliconName)
    new Metadata(this.sampleName, this.readId, ampliconYaml)
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
