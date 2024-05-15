#!/usr/bin/env bash

set -eux

declare DEPENDENCY_DIR=".dependencies"

# Put our dependencies in GitHub's PATH, on exit
trap 'echo "${GITHUB_WORKSPACE}/${DEPENDENCY_DIR}/bin" >> "${GITHUB_PATH}"' EXIT

# If the dependency directory exists, then it has been restored by the
# caching mechanism and there's nothing for us to do.
[[ -d "${DEPENDENCY_DIR}" ]] && exit

mkdir -p ${DEPENDENCY_DIR}/{bin,lib,share}
cd "${DEPENDENCY_DIR}"

## Download Groovy JARs ################################################
wget -O groovy.zip https://repo1.maven.org/maven2/org/codehaus/groovy/groovy-binary/3.0.21/groovy-binary-3.0.21.zip

unzip \
  -j \
  groovy.zip \
  "groovy-3.0.21/lib/extras-jaxb/*.jar" \
  "groovy-3.0.21/lib/*.jar" \
  -d lib

rm groovy.zip

## Download CodeNarc and dependencies ##################################
wget -O lib/GMetrics-2.1.0.jar   https://github.com/dx42/gmetrics/releases/download/v2.1.0/GMetrics-2.1.0.jar
wget -O lib/slf4j-nop-1.7.32.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-nop/1.7.32/slf4j-nop-1.7.32.jar
wget -O lib/CodeNarc-3.4.0.jar   https://github.com/CodeNarc/CodeNarc/releases/download/v3.4.0/CodeNarc-3.4.0.jar

## Download CodeNarc ruleset ##########################################
git clone \
  --single-branch --branch=v3.4.0 --depth=1 --filter=blob:none \
  https://github.com/CodeNarc/CodeNarc.git

mv CodeNarc/docs/StarterRuleSet-AllRulesByCategory.groovy.txt share/groovy.txt
rm -rf CodeNarc

## Download and build linter-rules-for-nextflow ########################
git clone \
  --single-branch --branch=main --depth=1 \
  https://github.com/awslabs/linter-rules-for-nextflow.git

# Extract HTTP(S) proxy settings for Gradle
source <(
  for _VAR in HTTP_PROXY HTTPS_PROXY; do
    printf "%s\t%s\n" "${_VAR}" "${!_VAR}"
  done \
  | awk -F '(://)|:|/|\t' '{
    print $1 "_HOST=" $3
    print $1 "_PORT=" $4
  }'
)

cd linter-rules-for-nextflow
./gradlew \
  -Dhttp.proxyHost="$HTTP_PROXY_HOST" \
  -Dhttp.proxyPort="$HTTP_PROXY_PORT" \
  -Dhttps.proxyHost="$HTTPS_PROXY_HOST" \
  -Dhttps.proxyPort="$HTTPS_PROXY_PORT" \
  --no-daemon \
  :linter-rules:build

mv linter-rules/build/libs/*.jar ../lib/
mv linter-rules/build/resources/main/rulesets/healthomics.xml ../share/nextflow.xml

cd ..
rm -rf linter-rules-for-nextflow

## Create wrapper script ###############################################
cat <<EOF >bin/CodeNarc
#!/usr/bin/env bash
java -classpath "\${GITHUB_WORKSPACE}/${DEPENDENCY_DIR}/lib/*" \\
     org.codenarc.CodeNarc \\
     "\$@"
EOF

chmod +x bin/CodeNarc
