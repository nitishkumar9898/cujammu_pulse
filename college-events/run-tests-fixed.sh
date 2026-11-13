#!/bin/bash
set -euo pipefail
export JAVA_HOME="/c/Users/NITISH KUMAR/toolchain/jdk/jdk-21.0.13+9"
export PATH="$JAVA_HOME/bin:/c/Users/NITISH KUMAR/toolchain/maven/apache-maven-3.9.9/bin:$PATH"
export MAVEN_OPTS="-Xmx4g"

echo "===Maven version==="
mvn -version

echo "===Test compile==="
mvn -o -q test-compile || {
  echo "compile failed"
  exit 1
}

echo "===Run tests (skip problems)==="
mvn -o -q test 2>&1 | tail -100
