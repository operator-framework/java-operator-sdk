#!/usr/bin/env bash

echo "Setting version to next release version"
mvn build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.incrementalVersion}
find . -name 'pom.xml' | xargs git add

RELEASE_VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)

read -p "Finish release of ${RELEASE_VERSION} by pushing it to Git (y/n)?" choice
if [[ $choice == 'y' ]]; then
  mvn versions:commit
  git commit -m "release version ${RELEASE_VERSION}"
  git push origin master

  echo "Setting new SNAPSHOT version"
  mvn build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.nextIncrementalVersion}-SNAPSHOT versions:commit
  NEW_VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
  find . -name 'pom.xml' | xargs git add
  git commit -m "set SNAPSHOT version: ${NEW_VERSION}"
  git push origin master
  echo "Finished release. New SNAPSHOT version is ${NEW_VERSION}"
else
  mvn versions:revert
fi
