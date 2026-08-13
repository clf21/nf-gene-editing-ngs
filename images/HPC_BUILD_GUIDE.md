# Building and Testing on HPC with Singularity

This guide covers building the v1.2 CRISPResso v1 stack as a Singularity image for HPC environments.

## Prerequisites

- Singularity/Apptainer installed (version 3.x or newer)
- Build permissions (sudo or --fakeroot capability)
- Git access to clone/update the repository
- Maven Central access (for baseCounts build) OR pre-built JAR

## Important Notes

### Python 2.7 End-of-Life Warning
⚠️ This build uses **Python 2.7**, which reached end-of-life on January 1, 2020. 

**Security Implications:**
- No security patches since 2020
- Should only be run in isolated container environments
- Do not expose to untrusted input

**Why Python 2.7?**
CRISPResso v1 (AGPL licensed) requires Python 2.7. CRISPResso2 (Python 3) has a proprietary license prohibiting commercial use without purchasing a license. See BUILD_NOTES.md for details.

## Building the Singularity Image

### Option 1: Using the build script (Recommended)

```bash
cd images/
./build-singularity.sh
```

This will create a `.sif` file like `nf-gene-editing-ngs_<commit-hash>.sif`

### Option 2: Manual build

```bash
cd images/

# With sudo
sudo singularity build nf-gene-editing-ngs_v1.2.sif Singularity.def

# Or with fakeroot (if user namespaces are enabled)
singularity build --fakeroot nf-gene-editing-ngs_v1.2.sif Singularity.def

# On many HPC systems, use --ignore-fakeroot-command
singularity build --force --ignore-fakeroot-command nf-gene-editing-ngs_v1.2.sif Singularity.def
```

### Build Time

Expected build time: 30-60 minutes depending on HPC resources and network speed
- Downloads: ~400MB
- Final image size: ~2-3GB
- Python 2 compilation is slower than Python 3

### Known Build Issues

#### Maven Central Access Blocked

If your HPC proxy blocks Maven Central, the baseCounts build will fail. 

**Solution 1: Build baseCounts externally**
```bash
# On a system with internet access
cd baseCounts
./mvnw clean package

# Copy the JAR to HPC
scp target/GeneEditing-*-jar-with-dependencies.jar hpc:/path/to/repo/baseCounts/target/

# Comment out Maven build in Singularity.def
# The JAR will be copied from the existing target/ directory
```

**Solution 2: Skip baseCounts**
If you don't need guide position base counting, comment out the baseCounts build section in Singularity.def.

#### EMBOSS FTP Server Timeout

The EMBOSS FTP server (ftp://emboss.open-bio.org) can be slow or unavailable.

**Symptoms:**
- Build hangs at EMBOSS download
- FTP connection timeout

**Solutions:**
- Retry the build (sometimes it works on second attempt)
- Cache the EMBOSS tarball and modify Singularity.def to use local copy
- Skip EMBOSS if not needed (check if your pipeline uses it)

## Testing the Image

### 1. Quick Smoke Test (Recommended)

```bash
cd images/
./test_container.sh nf-gene-editing-ngs_v1.2.sif
```

This automated test script verifies:
- All tools are installed and accessible
- CRISPResso v1 runs correctly
- Bowtie2 can build indexes and align reads
- Samtools can process BAM files
- End-to-end workflow with synthetic test data

### 2. Manual Tool Version Verification

```bash
# Check Bowtie2
singularity exec nf-gene-editing-ngs_v1.2.sif bowtie2 --version
# Expected: version 2.3.4.3

# Check Samtools
singularity exec nf-gene-editing-ngs_v1.2.sif samtools --version
# Expected: samtools 1.9

# Check Python version
singularity exec nf-gene-editing-ngs_v1.2.sif python --version
# Expected: Python 2.7.x

# Check CRISPResso
singularity exec nf-gene-editing-ngs_v1.2.sif CRISPResso --version
# Expected: CRISPResso 1.0.x

# Check Trimmomatic
singularity exec nf-gene-editing-ngs_v1.2.sif trimmomatic -version
# Expected: 0.39

# Check EMBOSS (if built)
singularity exec nf-gene-editing-ngs_v1.2.sif needleall --version
# Expected: EMBOSS:6.6.0.0

# Check baseCounts (if built)
singularity exec nf-gene-editing-ngs_v1.2.sif computeBaseCounts --help
```

### 3. Test CRISPResso with Custom Patches

The custom patches add important output columns. Test that they work:

```bash
# Run CRISPResso help to verify installation
singularity exec nf-gene-editing-ngs_v1.2.sif CRISPResso -h

# Test with minimal data (if available)
singularity exec nf-gene-editing-ngs_v1.2.sif CRISPResso \
  --fastq_r1 test_R1.fastq.gz \
  --fastq_r2 test_R2.fastq.gz \
  --amplicon_seq ATCGATCGATCG... \
  --guide_seq ATCGATCG \
  --output_folder test_output

# Verify output includes custom columns:
# - Frameshift, In_frame, Noncoding, Splice_mod columns in alleles table
```

## Deploying to HPC

### 1. Copy Image to Shared Storage

```bash
# Copy to project directory
cp nf-gene-editing-ngs_v1.2.sif /project/lab/containers/

# Or to scratch for testing
cp nf-gene-editing-ngs_v1.2.sif $SCRATCH/containers/
```

### 2. Set Environment Variable

Add to your `.bashrc` or job scripts:

```bash
export GENA_IMAGE="/project/lab/containers/nf-gene-editing-ngs_v1.2.sif"
```

### 3. Test with Nextflow

```bash
# Load Nextflow
module load nextflow

# Test with small dataset
nextflow run main.nf \
  -profile singularity,slurm \
  --metadata test_metadata.yml \
  --reference hg38 \
  --outdir test_results
```

## Nextflow Configuration for HPC

Example HPC profile in `nextflow.config`:

```groovy
profiles {
    hpc {
        singularity {
            enabled = true
            autoMounts = true
            runOptions = '--cleanenv --containall'
        }
        
        process {
            executor = 'slurm'
            queue = 'general'
            container = "${System.getenv('GENA_IMAGE') ?: '/project/lab/containers/nf-gene-editing-ngs_v1.2.sif'}"
            
            withLabel: high_memory {
                memory = '32 GB'
                time = '4h'
            }
        }
    }
}
```

## Troubleshooting

### Build fails with "permission denied"

Try using `--ignore-fakeroot-command`:
```bash
singularity build --force --ignore-fakeroot-command nf-gene-editing-ngs_v1.2.sif Singularity.def
```

### Python 2 pip/virtualenv issues

If you see errors about pip or virtualenv during build:
- This is expected with Python 2.7 EOL
- The build uses `python -m ensurepip` which is built into Python 2.7.9+
- Alpine 3.15 includes the last stable Python 2.7.18

### CRISPResso matplotlib font cache errors

If CRISPResso fails with font-related errors:
```bash
# Rebuild font cache inside container
singularity exec nf-gene-editing-ngs_v1.2.sif python -c "import matplotlib.pyplot"
```

### Container size too large

The image is 2-3GB due to:
- Python 2 dependencies
- EMBOSS (large bioinformatics suite)
- Build artifacts (if cleanup disabled)

To reduce size:
- Skip EMBOSS if not needed
- Enable cleanup in Singularity.def (if build system allows)

## Performance Considerations

### Python 2.7 Performance
- Generally slower than Python 3
- NumPy/SciPy operations may be less optimized
- Consider allocating 10-20% more time for CRISPResso jobs compared to modern tools

### Alpine 3.15 Compatibility
- Alpine 3.15 was released in November 2021
- Compatible with most HPC systems as of 2024-2026
- Uses musl libc instead of glibc (generally not an issue)

## Security Recommendations

Since Python 2.7 is EOL:

1. **Network Isolation**: Run jobs without network access when possible
2. **Input Validation**: Validate all input files before processing
3. **Access Control**: Restrict container to trusted users only
4. **Monitoring**: Watch for unusual behavior or resource usage
5. **Updates**: Check for updated dependencies within Alpine 3.15 constraints

## Related Documentation

- BUILD_NOTES.md - Detailed build notes and version history
- test_container.sh - Automated testing script
- Singularity.def - Container definition file
- CRISPResso v1 repository: https://github.com/lucapinello/CRISPResso

## Getting Help

If you encounter issues:

1. Check BUILD_NOTES.md for known issues
2. Review Singularity build logs
3. Test individual tools with `singularity exec`
4. Verify file paths and permissions
5. Check HPC module conflicts

## Version Information

- **Container Version**: v1.2
- **Python**: 2.7.18 (EOL since 2020)
- **Alpine**: 3.15
- **CRISPResso**: v1 (AGPL license)
- **License**: AGPL v3 (allows commercial use)
