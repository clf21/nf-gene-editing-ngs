## Build Phase #########################################################

FROM alpine:3.15 AS build-base

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
      python2-dev \
      wget \
      xz-dev \
      zlib-dev

# Download, patch and build Bowtie2 2.3.4.3
FROM build-base AS build-bowtie2

COPY bowtie2.patch /tmp/bowtie2/bowtie2.patch

RUN cd /tmp/bowtie2 \
 && wget -O bowtie2.zip https://downloads.sourceforge.net/project/bowtie-bio/bowtie2/2.3.4.3/bowtie2-2.3.4.3-source.zip \
 && unzip bowtie2.zip \
 && patch -p0 < bowtie2.patch \
 && cd bowtie2-2.3.4.3 \
 && make

# Download, patch and build CRISPResso `master` (and its vendored dependencies)
FROM build-base AS build-crispresso

COPY install-CRISPResso.sh CRISPResso.patch /tmp/CRISPResso/

RUN cd /tmp/CRISPResso \
 && wget -O CRISPResso.zip https://github.com/lucapinello/CRISPResso/archive/master.zip \
 && unzip CRISPResso.zip \
 && patch -p0 < CRISPResso.patch \
 && cd CRISPResso-master/dependencies \
 && tar -xf FLASH-1.2.11.tar.gz \
 && cd FLASH-1.2.11 \
 && make \
 && cd ../../.. \
 && python -m ensurepip \
 && pip install virtualenv \
 && ./install-CRISPResso.sh /tmp/CRISPResso/CRISPResso-master /opt/CRISPResso/venv

# Skip EMBOSS - not used in pipeline and FTP server unreliable

# Download, build and install Samtools 1.9
FROM build-base AS build-samtools

RUN mkdir -p /tmp/samtools \
 && cd /tmp/samtools \
 && wget -O samtools.tar.gz https://github.com/samtools/samtools/releases/download/1.9/samtools-1.9.tar.bz2 \
 && tar -xf samtools.tar.gz \
 && cd samtools-1.9 \
 && ./configure --prefix=/opt/samtools \
 && make \
 && make install

# Download Trimmomatic 0.39
FROM build-base AS build-trimmomatic

RUN mkdir -p /tmp/trimmomatic \
 && cd /tmp/trimmomatic \
 && wget -O trimmomatic.zip https://github.com/usadellab/Trimmomatic/files/5854859/Trimmomatic-0.39.zip \
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

FROM alpine:3.15

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
      python2 \
      xz-libs \
      zlib

# Install Bowtie2
COPY --from=build-bowtie2 \
  /tmp/bowtie2/bowtie2-2.3.4.3/bowtie2 \
  /tmp/bowtie2/bowtie2-2.3.4.3/bowtie2-align-s \
  /tmp/bowtie2/bowtie2-2.3.4.3/bowtie2-align-l \
  /tmp/bowtie2/bowtie2-2.3.4.3/bowtie2-build \
  /tmp/bowtie2/bowtie2-2.3.4.3/bowtie2-build-s \
  /tmp/bowtie2/bowtie2-2.3.4.3/bowtie2-build-l \
  /tmp/bowtie2/bowtie2-2.3.4.3/bowtie2-inspect \
  /tmp/bowtie2/bowtie2-2.3.4.3/bowtie2-inspect-s \
  /tmp/bowtie2/bowtie2-2.3.4.3/bowtie2-inspect-l \
  /opt/bowtie2/bin/

# Install CRISPResso
COPY --from=build-crispresso /tmp/CRISPResso/CRISPResso-master/dependencies/FLASH-1.2.11/flash /opt/CRISPResso/bin/
COPY --from=build-crispresso /opt/CRISPResso/venv /opt/CRISPResso/venv

COPY --chmod=755 <<'EOF' /opt/CRISPResso/bin/_crispresso
#!/bin/sh
. /opt/CRISPResso/venv/bin/activate
"$(basename "$0")" "$@"
EOF

RUN cd /opt/CRISPResso/bin \
 && ln -s _crispresso CRISPResso \
 && ln -s _crispresso CRISPRessoCompare \
 && ln -s _crispresso CRISPRessoCount \
 && ln -s _crispresso CRISPRessoPooled \
 && ln -s _crispresso CRISPRessoPooledWGSCompare \
 && ln -s _crispresso CRISPRessoWGS

# EMBOSS skipped - not used in pipeline

# Install Samtools
COPY --from=build-samtools /opt/samtools /opt/samtools

# Install Trimmomatic
COPY --from=build-trimmomatic /tmp/trimmomatic/Trimmomatic-0.39 /opt/trimmomatic

COPY --chmod=755 <<'EOF' /opt/trimmomatic/bin/trimmomatic
#!/bin/sh
exec java -jar /opt/trimmomatic/trimmomatic-0.39.jar "$@"
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
ENV PATH $PATH:/opt/bowtie2/bin:/opt/CRISPResso/bin:/opt/samtools/bin:/opt/trimmomatic/bin:/opt/base-counts/bin

# Run CRISPResso once to have Matplotlib generate its font cache
RUN CRISPResso -h >/dev/null
