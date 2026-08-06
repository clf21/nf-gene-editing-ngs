# Building and Testing on HPC with Singularity

This guide covers building the Phase 1 dependency updates as a Singularity image for HPC environments.

## Prerequisites

- Singularity/Apptainer installed (version 3.x or newer)
- Build permissions (sudo or --fakeroot capability)
- Git access to clone/update the repository

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
sudo singularity build nf-gene-editing-ngs.sif Singularity.def

# Or with fakeroot (if user namespaces are enabled)
singularity build --fakeroot nf-gene-editing-ngs.sif Singularity.def
```

### Build Time

Expected build time: 20-45 minutes depending on HPC resources
- Downloads: ~500MB
- Final image size: ~2-3GB

## Testing the Image

### 1. Verify Tool Versions

```bash
# Check Bowtie2
singularity exec nf-gene-editing-ngs_*.sif bowtie2 --version
# Expected: version 2.5.5

# Check Samtools
singularity exec nf-gene-editing-ngs_*.sif samtools --version
# Expected: samtools 1.23.1

# Check Trimmomatic
singularity exec nf-gene-editing-ngs_*.sif trimmomatic -version
# Expected: 0.41

# Check Python
singularity exec nf-gene-editing-ngs_*.sif python3 --version
# Expected: Python 3.11.x (Alpine 3.21 default)

# Check CRISPResso (still version 1.x in Phase 1)
singularity exec nf-gene-editing-ngs_*.sif CRISPResso --version
```

### 2. Configure Nextflow to Use the Image

Set environment variable:
```bash
export GENA_IMAGE="/path/to/nf-gene-editing-ngs_<commit>.sif"
```

Or update your Nextflow config file:
```groovy
singularity {
    enabled = true
    autoMounts = true
}

process {
    container = '/path/to/nf-gene-editing-ngs_<commit>.sif'
}
```

### 3. Run the Test Suite

```bash
cd /path/to/nf-gene-editing-ngs

# Set the Singularity image
export GENA_IMAGE="$(pwd)/images/nf-gene-editing-ngs_*.sif"

# Run tests
./run_tests tests/main.nf.test
```

### 4. Run a Small Test Pipeline

If you have test data:

```bash
nextflow run main.nf \
  -profile singularity,slurm \
  --metadata /path/to/test-metadata.yml \
  --reference hg38 \
  --outdir results_phase1_test
```

## Validation Checklist

Phase 1 validation should verify:

- [ ] Image builds successfully
- [ ] All tools show correct versions
- [ ] Bowtie2 aligns reads correctly
- [ ] Samtools BAM operations work
- [ ] Trimmomatic trims reads
- [ ] CRISPResso runs (still Python 2 version in Phase 1)
- [ ] Test suite passes
- [ ] Pipeline completes on small dataset

## Known Issues

### Python 2 Warning (Phase 1)

Phase 1 still uses Python 2 for CRISPResso1. You may see deprecation warnings:
```
Python 2 is EOL and will not receive security updates
```

This is expected and will be resolved in Phase 2 (CRISPResso2 migration with Python 3).

### Build Errors

If you encounter permission errors:
```bash
# Check if fakeroot is available
singularity build --fakeroot --help

# Or request sudo access for the build
sudo singularity build ...
```

If Alpine package downloads fail:
- Check internet connectivity from HPC
- Try using HPC proxy settings if required
- Some HPC systems require pre-downloading packages

### SLURM Integration

If using SLURM, ensure your Nextflow profile includes:
```groovy
process {
    executor = 'slurm'
    queue = 'your-queue-name'
    
    // Resource limits
    memory = '6 GB'
    cpus = 1
    time = '4h'
}
```

## Phase 1 vs Phase 2

**Phase 1 (Current):**
- Alpine 3.21
- Python 3 installed but CRISPResso1 still uses Python 2
- Updated: Bowtie2, Samtools, Trimmomatic
- Safe baseline before major CRISPResso2 migration

**Phase 2 (Next):**
- CRISPResso1 → CRISPResso2
- Full Python 3 migration
- Remove Python 2 entirely
- Higher risk, requires thorough validation

## Troubleshooting

### Image Won't Build on Compute Nodes

Some HPC systems restrict Singularity builds on compute nodes. Build on:
- Login nodes (if permitted)
- Dedicated build nodes
- Local workstation, then transfer .sif file

### Test Suite Failures

Compare outputs against baseline (v1.2):
```bash
# Save current results
nextflow run main.nf ... --outdir results_v1.2

# Test new image
export GENA_IMAGE=nf-gene-editing-ngs_*.sif
nextflow run main.nf ... --outdir results_v1.3

# Compare key metrics
diff results_v1.2/summary_table.txt results_v1.3/summary_table.txt
```

## Support

For issues specific to:
- **Nextflow**: Check `nextflow.log`
- **Singularity**: Check `.nextflow.log` and `work/` task directories
- **SLURM**: Check job output files in `work/` directories

## Next Steps After Phase 1 Validation

Once Phase 1 passes validation:
1. Tag as v1.3.0
2. Document any alignment/trimming differences
3. Prepare for Phase 2 (CRISPResso2 migration)
4. Archive v1.2 image as fallback
