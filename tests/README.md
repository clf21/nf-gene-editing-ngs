# Testing the Pipeline

## Quick Start

To run the pipeline tests:

```console
$ ./run_tests [TEST_FILE] [NF-TEST_OPTIONS...]
```

As with the `run` script, the `run_tests` script will attempt to select the
appropriate container and execution profiles for your environment. The image
(and tag) can be overridden using the `IMAGE` (and `TAG`) environment variables.

Pipeline tests use the [nf-test](https://www.nf-test.com) framework, and the
`nf-test` command must be available to run the tests. See the nf-test
documentation for command line options that can be passed through with the
`run_tests` script. Use the `--tag` option to run individual tests or a subset
of tests.

## Test data

Publicly available NGS datasets are included in the `tests/test-data/fastq`
folder for running the tests. All samples were downsampled by taking the first
25,000 reads. Files were given names matching the Illumina file naming
conventions so they could be run under the default parameters.

The sources of the test data are

- NHEJ samples:
    [SRR16178872](https://www.ncbi.nlm.nih.gov/sra/?term=SRR16178872) (AAVS1
    site) and [SRR16179569](https://www.ncbi.nlm.nih.gov/sra/?term=SRR16179569)
    (CCR5 site) from [Yin, et al.](https://doi.org/10.1016/j.ymthe.2022.11.014)
- HDR sample:
    [SRR11975752](https://www.ncbi.nlm.nih.gov/sra/?term=SRR11975752) (EcoRI
    site knocked-in to ACTA2) from
    [Schubert, et al.](https://doi.org/10.1038/s41598-021-98965-y)
- Base editing sample:
    [SRR3305503](https://www.ncbi.nlm.nih.gov/sra/?term=SRR3305503) (BE3 editor
    targeting EMX1) from [Komor, et al.](https://doi.org/10.1038/nature17946)
    (this is also a single-read sample and serves to test the single-read part
    of the pipeline)
