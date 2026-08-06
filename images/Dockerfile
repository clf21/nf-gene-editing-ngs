## Build Phase #########################################################

FROM alpine:3.21 AS build-base

# Install build dependencies
RUN apk --no-cache add \
      build-base \
      bzip2-dev \
      curl-dev \
      freetype-dev \
      libpng-dev \
      libtbb-dev \
      ncurses-dev \
      openblas-dev \
      openjdk11 \
      perl-app-cpanminus \
      perl-dev \
      python3-dev \
      wget \
      xz-dev \
      zlib-dev

# Download and build Bowtie2 2.5.5
FROM build-base AS build-bowtie2

RUN mkdir -p /tmp/bowtie2 \
 && cd /tmp/bowtie2 \
 && wget -O bowtie2.tar.gz https://github.com/BenLangmead/bowtie2/archive/refs/tags/v2.5.5.tar.gz \
 && tar -xf bowtie2.tar.gz \
 && cd bowtie2-2.5.5 \
 && make
# Note: Removed bowtie2.patch - it was for 2.3.4.3 and only fixed trailing whitespace

# Install CRISPResso2 (Python 3 compatible)
FROM build-base AS build-crispresso

RUN python3 -m venv /opt/CRISPResso2/venv \
 && . /opt/CRISPResso2/venv/bin/activate \
 && pip install --no-cache-dir CRISPResso2

# Download, build and install EMBOSS 6.6.0
FROM build-base AS build-emboss

RUN mkdir -p /tmp/emboss \
 && cd /tmp/emboss \
 && wget -O emboss.tar.gz ftp://emboss.open-bio.org/pub/EMBOSS/EMBOSS-6.6.0.tar.gz \
 && tar -xf emboss.tar.gz \
 && cd EMBOSS-6.6.0 \
 && ./configure --prefix=/opt/emboss --without-x \
 && make \
 && make install

# Download, build and install Samtools 1.23.1
FROM build-base AS build-samtools

RUN mkdir -p /tmp/samtools \
 && cd /tmp/samtools \
 && wget -O samtools.tar.bz2 https://github.com/samtools/samtools/releases/download/1.23.1/samtools-1.23.1.tar.bz2 \
 && tar -xf samtools.tar.bz2 \
 && cd samtools-1.23.1 \
 && ./configure --prefix=/opt/samtools \
 && make \
 && make install

# Download Trimmomatic 0.41
FROM build-base AS build-trimmomatic

RUN mkdir -p /tmp/trimmomatic \
 && cd /tmp/trimmomatic \
 && wget -O trimmomatic.zip https://github.com/usadellab/Trimmomatic/files/14508250/Trimmomatic-0.41.zip \
 && unzip trimmomatic.zip

# Install Perl dependencies
FROM build-base AS build-perl_dependencies

RUN cpanm YAML::XS

# Build guide position base counting tool
FROM build-base AS build-base-count

COPY baseCounts /tmp/baseCounts

RUN cd /tmp/baseCounts \
 && ./mvnw clean package

## Runtime Phase #######################################################

FROM alpine:3.21

# Install runtime dependencies
RUN apk --no-cache add \
      bash \
      coreutils \
      freetype \
      graphviz \
      libbz2 \
      libcurl \
      libpng \
      libtbb \
      ncurses-libs \
      openblas \
      openjdk11-jre-headless \
      perl \
      python3 \
      xz-libs \
      zlib

# Install Bowtie2
COPY --from=build-bowtie2 \
  /tmp/bowtie2/bowtie2-2.5.5/bowtie2 \
  /tmp/bowtie2/bowtie2-2.5.5/bowtie2-align-s \
  /tmp/bowtie2/bowtie2-2.5.5/bowtie2-align-l \
  /tmp/bowtie2/bowtie2-2.5.5/bowtie2-build \
  /tmp/bowtie2/bowtie2-2.5.5/bowtie2-build-s \
  /tmp/bowtie2/bowtie2-2.5.5/bowtie2-build-l \
  /tmp/bowtie2/bowtie2-2.5.5/bowtie2-inspect \
  /tmp/bowtie2/bowtie2-2.5.5/bowtie2-inspect-s \
  /tmp/bowtie2/bowtie2-2.5.5/bowtie2-inspect-l \
  /opt/bowtie2/bin/

# Install CRISPResso2
COPY --from=build-crispresso /opt/CRISPResso2/venv /opt/CRISPResso2/venv

COPY --chmod=755 <<'EOF' /opt/CRISPResso2/bin/_crispresso
#!/bin/sh
. /opt/CRISPResso2/venv/bin/activate
"$(basename "$0")" "$@"
EOF

RUN cd /opt/CRISPResso2/bin \
 && ln -s _crispresso CRISPResso \
 && ln -s _crispresso CRISPResso2 \
 && ln -s _crispresso CRISPRessoCompare \
 && ln -s _crispresso CRISPRessoWGS \
 && ln -s _crispresso CRISPRessoPooled \
 && ln -s _crispresso CRISPRessoAggregate

# Install EMBOSS
COPY --from=build-emboss /opt/emboss /opt/emboss

# Install Samtools
COPY --from=build-samtools /opt/samtools /opt/samtools

# Install Trimmomatic
COPY --from=build-trimmomatic /tmp/trimmomatic/Trimmomatic-0.41 /opt/trimmomatic

COPY --chmod=755 <<'EOF' /opt/trimmomatic/bin/trimmomatic
#!/bin/sh
exec java -jar /opt/trimmomatic/trimmomatic-0.41.jar "$@"
EOF

# Install guide position base counting tool
COPY --from=build-base-count \
  /tmp/baseCounts/target/GeneEditing-*-jar-with-dependencies.jar \
  /opt/base-counts/lib/GeneEditing.jar

COPY --chmod=755 <<'EOF' /opt/base-counts/bin/computeBaseCounts
#!/bin/sh
exec java -jar /opt/base-counts/lib/GeneEditing.jar "$@"
EOF

# Install Perl dependencies
COPY --from=build-perl_dependencies /usr/local/lib/perl5/site_perl /usr/local/lib/perl5/site_perl

# Add binaries to PATH
ENV PATH $PATH:/opt/bowtie2/bin:/opt/CRISPResso2/bin:/opt/emboss/bin:/opt/samtools/bin:/opt/trimmomatic/bin:/opt/base-counts/bin

# Run CRISPResso2 once to have Matplotlib generate its font cache
RUN CRISPResso -h >/dev/null
