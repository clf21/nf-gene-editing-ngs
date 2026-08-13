# Build Notes - v1.2 (CRISPResso v1)

## Current Version: v1.2

**Status:** Production (with licensing constraints - see below)

**Build Command:**
```bash
apptainer build --force --ignore-fakeroot-command nf-gene-editing-ngs_v1.2.sif Singularity.def
```

## Software Stack

### Core Components
- **Alpine Linux**: 3.15
- **Python**: 2.7 (⚠️ EOL since January 2020)
- **Bowtie2**: 2.3.4.3 (with patch)
- **Samtools**: 1.9
- **Trimmomatic**: 0.39
- **CRISPResso**: v1 (Python 2, master branch with custom patches)
- **EMBOSS**: 6.6.0

### Licensing

**CRISPResso v1** is licensed under **AGPL v3**:
- ✅ **Commercial use allowed** (with source disclosure requirements)
- ✅ **Free and open source**
- ⚠️ **Archived/deprecated** since June 2019
- 📍 Repository: https://github.com/lucapinello/CRISPResso

**Why not CRISPResso2?**
CRISPResso2 uses a proprietary academic license that prohibits commercial use without purchasing a license from Massachusetts General Hospital (licensing@edilytics.com).

### Custom Patches

**CRISPResso.patch** adds critical functionality:
- Frameshift tracking in output
- In-frame modification tracking
- Noncoding modification tracking
- Splice modification tracking
- Cut point position annotations

**bowtie2.patch** fixes trailing whitespace issues in version 2.3.4.3.

## Important Warnings

### Python 2.7 End-of-Life
⚠️ **Python 2.7 reached end-of-life on January 1, 2020**

**Implications:**
- No security updates since 2020
- Growing incompatibility with modern systems
- Alpine 3.15 is one of the last versions with Python 2 support

**Risk Mitigation:**
- Run only in isolated containers
- Do not expose to untrusted input
- Monitor for security advisories
- Plan migration path if CRISPResso2 license becomes available

### Dependency Age
All dependencies are from 2018-2019 era:
- Bowtie2 2.3.4.3 (2018) - current is 2.5.x
- Samtools 1.9 (2018) - current is 1.20+
- Alpine 3.15 (2021) - current is 3.21+

## Build Requirements

**Required Files:**
- `images/Singularity.def` - Container definition
- `images/bowtie2.patch` - Bowtie2 whitespace fix
- `images/CRISPResso.patch` - Custom CRISPResso modifications
- `images/install-CRISPResso.sh` - CRISPResso installation script
- `baseCounts/` - Java-based guide position counting tool

**HPC Considerations:**
- Maven Central access required for baseCounts build
- If Maven blocked by proxy, build baseCounts separately and copy JAR
- Use `--ignore-fakeroot-command` flag on HPC systems without fakeroot

## Verification Steps

### Quick Smoke Test

Run the automated container test script:

```bash
cd images
./test_container.sh nf-gene-editing-ngs_v1.2.sif
```

This tests:
- All tools are installed and accessible
- CRISPResso v1 runs correctly
- Bowtie2 can build indexes and align reads
- Samtools can process BAM files
- Complete workflow with synthetic test data

### Manual Version Check

```bash
# Check versions
singularity exec nf-gene-editing-ngs_v1.2.sif bowtie2 --version
singularity exec nf-gene-editing-ngs_v1.2.sif samtools --version
singularity exec nf-gene-editing-ngs_v1.2.sif python --version
singularity exec nf-gene-editing-ngs_v1.2.sif CRISPResso --version
singularity exec nf-gene-editing-ngs_v1.2.sif trimmomatic -version

# Expected output:
# - Bowtie2: 2.3.4.3
# - Samtools: 1.9
# - Python: 2.7.x
# - CRISPResso: 1.0.x (from master branch)
# - Trimmomatic: 0.39
```

## Running the Pipeline

Set the Singularity image:
```bash
export GENA_IMAGE="/path/to/nf-gene-editing-ngs_v1.2.sif"
```

Or update Nextflow config:
```groovy
singularity {
    enabled = true
    autoMounts = true
}

process {
    container = '/path/to/nf-gene-editing-ngs_v1.2.sif'
}
```

Run pipeline:
```bash
nextflow run main.nf \
  -profile singularity,slurm \
  --metadata /path/to/metadata.yml \
  --reference hg38 \
  --outdir results
```

## Troubleshooting

### Build Issues

**Maven Central blocked on HPC:**
```bash
# Build baseCounts on a system with internet access
cd baseCounts
./mvnw clean package

# Copy the JAR to HPC and modify Singularity.def to skip Maven build
```

**EMBOSS FTP server unreliable:**
The EMBOSS FTP server (ftp://emboss.open-bio.org) can be slow or unavailable. If the build hangs:
- Use a mirror or cache the tarball
- Increase download timeout
- Build without EMBOSS if not needed for your analyses

### Pipeline Issues

**Pipeline fails with missing tools:**
Check that `GENA_IMAGE` environment variable is set correctly.

**Python 2 compatibility errors:**
Some modern filesystems or kernels may have issues with Python 2.7. Ensure:
- Container has sufficient permissions
- No conflicting Python environment variables

## Version History

### v1.2 (Current)
- **Date:** 2024-2026 baseline
- **Status:** Reverted from v2.0 due to CRISPResso2 licensing restrictions
- **License:** AGPL v3 (commercial use allowed)
- **Stack:** Python 2.7, Alpine 3.15, CRISPResso v1

### v2.0 (Not Released - Licensing Issues)
- **Date:** 2026-08-06
- **Status:** Abandoned due to CRISPResso2 proprietary license
- **Stack:** Python 3, Alpine 3.23, CRISPResso2
- **Issue:** CRISPResso2 prohibits commercial use without purchased license

## Future Considerations

### Migration Path Options

1. **Purchase CRISPResso2 license** (Best technical solution)
   - Contact: licensing@edilytics.com
   - Enables Python 3 migration
   - Access to maintained software

2. **Alternative tools** (Long-term)
   - Investigate other CRISPR analysis tools with compatible licenses
   - Evaluate inDelphi, CRISPRitz, or other open-source alternatives

3. **Maintain v1.2** (Current approach)
   - Accept Python 2.7 EOL risks
   - Run in isolated environment only
   - Monitor for security issues

## Files Modified from Original

**Container build:**
- `images/Dockerfile` - Docker build (for local testing)
- `images/Singularity.def` - Apptainer/Singularity build (HPC production)
- `images/build-singularity.sh` - Build script

**Pipeline configuration:**
- `conf/modules.config` - No special flags needed for v1

**Documentation:**
- `images/BUILD_NOTES.md` - This file
- `images/HPC_BUILD_GUIDE.md` - HPC-specific build instructions

## Related Documentation

- CRISPResso v1 repository: https://github.com/lucapinello/CRISPResso
- CRISPResso v1 license (AGPL v3): https://github.com/lucapinello/CRISPResso/blob/master/LICENSE
- Python 2.7 EOL announcement: https://www.python.org/doc/sunset-python-2/
