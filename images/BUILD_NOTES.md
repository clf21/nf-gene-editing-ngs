# Build Notes - Phase 2 Complete

## Successfully Built: v2.0.0

**Build Command:**
```bash
apptainer build --force --ignore-fakeroot-command nf-gene-editing-ngs_v2.0.0.sif Singularity.def
```

## What Was Updated

### Core Updates (Phase 1 + Phase 2 Combined)
- ✅ **Alpine Linux**: 3.15 → 3.23
- ✅ **Python**: 2.7 → 3.12
- ✅ **Bowtie2**: 2.3.4.3 → 2.5.5
- ✅ **Samtools**: 1.9 → 1.23.1
- ✅ **Trimmomatic**: 0.39 (no newer version available)
- ✅ **CRISPResso**: 1.x (Python 2) → CRISPResso2 (Python 3, master branch)

### Removed
- ❌ **EMBOSS**: Removed (unused in pipeline, FTP server unreliable)
- ⏸️ **baseCounts**: Disabled (HPC proxy blocks Maven Central)

## Key Build Adjustments for HPC

1. **No cleanup step**: Cleanup causes build failure with `--ignore-fakeroot-command`
   - Image is larger but functional
   
2. **CRISPResso2 compatibility mode**: Added `--crispresso1_mode` flag in `/conf/modules.config`
   - Maintains backward compatibility with existing outputs

3. **Simplified paths**: Used `/build` instead of `/opt/build-files` to match working builds

## Verification Steps

Test that tools are installed and working:

```bash
# Check versions
singularity exec nf-gene-editing-ngs_v2.0.0.sif bowtie2 --version
singularity exec nf-gene-editing-ngs_v2.0.0.sif samtools --version
singularity exec nf-gene-editing-ngs_v2.0.0.sif python3 --version
singularity exec nf-gene-editing-ngs_v2.0.0.sif CRISPResso --version
singularity exec nf-gene-editing-ngs_v2.0.0.sif trimmomatic -version

# Expected output:
# - Bowtie2: 2.5.5
# - Samtools: 1.23.1
# - Python: 3.12.x
# - CRISPResso2: (version from master branch)
# - Trimmomatic: 0.39
```

## Running the Pipeline

Set the Singularity image:
```bash
export GENA_IMAGE="/path/to/nf-gene-editing-ngs_v2.0.0.sif"
```

Or update Nextflow config:
```groovy
singularity {
    enabled = true
    autoMounts = true
}

process {
    container = '/path/to/nf-gene-editing-ngs_v2.0.0.sif'
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

## Known Issues

### baseCounts Tool Missing
The `computeBaseCounts` tool (used for guide position base counting) is not included because:
- HPC proxy blocks Maven Central
- Maven dependencies cannot be downloaded during build

**Workarounds:**
1. Build the JAR on a system with internet access and copy into container
2. Request Maven Central access through HPC proxy
3. Use a local Maven repository mirror

### Image Size
Due to no cleanup, the image is larger (~3-4GB) and contains:
- Build temporary files in `/tmp`
- Source code in `/build`
- Build dependencies

This doesn't affect functionality, just disk space.

## Validation Checklist

Before production use, validate:

- [ ] Bowtie2 alignments produce correct SAM/BAM files
- [ ] Samtools operations work correctly
- [ ] CRISPResso2 produces expected outputs with `--crispresso1_mode`
- [ ] Trimmomatic trims reads correctly
- [ ] Pipeline completes on test dataset
- [ ] Compare results with v1.2 baseline (should be within ±1% editing efficiency)

## Next Steps

1. **Run test suite**: `./run_tests tests/main.nf.test`
2. **Test on small dataset**: Validate outputs match expected results
3. **Scientific validation**: Compare with v1.2 outputs
4. **Address baseCounts**: If needed for your analyses
5. **Tag as v2.0.0**: If validation passes

## Troubleshooting

### Pipeline fails with missing tools
Check that `GENA_IMAGE` environment variable is set correctly.

### CRISPResso outputs different from v1.2
The `--crispresso1_mode` flag should maintain compatibility. If outputs differ significantly:
- Check CRISPResso2 version: `singularity exec <image> CRISPResso --version`
- Review CRISPResso2 changelog for breaking changes
- Consider pinning to a specific CRISPResso2 commit

### Performance issues
CRISPResso2 may have different performance characteristics than CRISPResso1.
Monitor resource usage and adjust SLURM job settings if needed.

## Files Modified

**Container build:**
- `images/Dockerfile` - Docker build (for local testing)
- `images/Singularity.def` - Apptainer/Singularity build
- `images/build-singularity.sh` - Build script

**Pipeline configuration:**
- `conf/modules.config` - Added `--crispresso1_mode` flag

**Documentation:**
- `images/HPC_BUILD_GUIDE.md` - HPC build instructions
- `images/BUILD_NOTES.md` - This file

## Build History

- Commit `db1ac78`: Successfully built v2.0.0 with all Phase 2 updates
- Branch: `update-dependencies`
- Date: 2026-08-06
