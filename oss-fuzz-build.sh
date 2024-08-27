#!/bin/bash -eu
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Read command line arguments
if [[ "$#" -ne 1 || -z "${JVM_LD_LIBRARY_PATH:-}" ]]; then
  cat >&2 <<EOF
Generates fuzzer runner scripts to be employed by Google OSS-Fuzz.
See "FUZZING.adoc" for details.

Usage: $0 <outputDir>

  outputDir

    The output directory to dump runner scripts and their dependencies.

Environment variables:

  BUILD (optional)

    If "false", Maven installation will be skipped.

  FORCE_DOWNLOAD (optional)

    If "true", certain externally downloaded files (corpus, dictionary, etc.)
    will be downloaded anyway and override existing ones.

  JVM_LD_LIBRARY_PATH (required)

    Directory containing the Java shared libraries, e.g.,
    "/usr/lib/jvm/zulu17/lib/server".
EOF
  exit 1
fi
outputDir=$(readlink -f "$1")

# Ensure output directory exists
mkdir -p "$outputDir"

# Switch to the script directory
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

# To contain all Maven dependencies under `$outputDir`, explicitly provide the Maven local repository.
# I presume(!?) this helps with caching and reproducibility.
export MAVEN_OPTS="-Dmaven.repo.local=$outputDir/.m2"

# Make Maven executions scripting friendly
export MAVEN_ARGS="--batch-mode --no-transfer-progress --errors"

# Determine the project version
projectVersion=$(./mvnw \
  --quiet -DforceStdout=true \
  -Dexpression=project.version \
  help:evaluate \
  | tail -n 1)

# Build the project
[ "${BUILD:-}" != "false" ] && ./mvnw \
  -Dmaven.test.skip=true \
  -Dxml.skip \
  -Dcyclonedx.skip \
  -Dbnd.baseline.skip \
  -Drat.skip \
  -Dspotless.skip \
  -Dspotbugs.skip \
  -Denforcer.skip \
  install

# Download the JSON dictionary
jsonDictPath="$outputDir/json.dict"
[[ ! -f "$jsonDictPath" || "${FORCE_DOWNLOAD:-}" = "true" ]] && \
  wget --quiet https://raw.githubusercontent.com/google/fuzzing/master/dictionaries/json.dict -O "$jsonDictPath"

# Download the JSON seed corpus
jsonSeedCorpusPath="$outputDir/json_seed_corpus.zip"
if [[ ! -f "$jsonSeedCorpusPath" || "${FORCE_DOWNLOAD:-}" = "true" ]]; then
  git clone --quiet --depth 1 https://github.com/dvyukov/go-fuzz-corpus
  zip -q -j "$jsonSeedCorpusPath" go-fuzz-corpus/json/corpus/*
  rm -rf go-fuzz-corpus
fi

# Iterate over fuzzers
for module in *-fuzz-test; do

  # Copy the built module artifact to the output folder
  cp "$PWD/$module/target/$module-$projectVersion.jar" "$outputDir/$module-$projectVersion.jar"

  # Determine the Java class path
  ./mvnw dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -pl $module
  classPath="$(sed "s|$outputDir/||g" /tmp/cp.txt):$module-$projectVersion.jar"
  rm /tmp/cp.txt

  for fuzzer in $(find "$module" -name "*Fuzzer.java"); do

    # Create the runner script
    fqcn=$(echo "$fuzzer" | sed -r 's|^'$module'/src/main/java/(.+)\.java|\1|' | tr / .)
    className="${fqcn##*.}"
    scriptPath="$outputDir/$module-$className"
    cat >"$scriptPath" <<EOF
#!/bin/bash -eu
#
# This file is auto-generated by https://github.com/apache/logging-log4j2/tree/2.x/oss-fuzz-build.sh
#

# OSS-Fuzz detects fuzzers by checking the presence of the magical "LLVMFuzzerTestOneInput" word, hence this line.

# Switch to the script directory
cd -- "\$(dirname -- "\${BASH_SOURCE[0]}")"

# Determine JVM args
if [[ "\$@" =~ (^| )-runs=[0-9]+(\$| ) ]]; then
  jvmArgs="-Xmx1900m:-Xss900k"
else
  jvmArgs="-Xmx2048m:-Xss1024k"
fi

# Temporarily increase verbosity to troubleshoot fuzzer failures
# See https://github.com/google/oss-fuzz/issues/12349
set -x

# Verify the classpath
classPath="$classPath"
echo "\$classPath" | sed 's/:/\n/g' | while read classPathFile; do
  if [[ ! -f "\$classPathFile" ]]; then
    echo "ERROR: Could not find class path file: \"\$classPathFile\"" 1>&2
    echo "ERROR: Complete class path: \"\$classPath\"" 1>&2
    exit 1
  fi
done

# Run the fuzzer
LD_LIBRARY_PATH="\$JVM_LD_LIBRARY_PATH":. \\
jazzer_driver \\
  --agent_path=jazzer_agent_deploy.jar \\
  --cp="\$classPath" \\
  --target_class="$fqcn" \\
  --jvm_args="\$jvmArgs" \\
  \$@
EOF
    chmod +x "$scriptPath"

    # Execute fuzzer setup scripts
    grep "^// FUZZER-SETUP-SCRIPT " "$fuzzer" | while read _ _ setupScript; do
      eval $setupScript
    done

  done

done
