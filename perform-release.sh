#!/usr/bin/env bash
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "master" ]; then
  echo "Only run releases from master branch (current is '$BRANCH')"
  exit 0
fi

echo "Setting version to next release version"
mvn -q build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.incrementalVersion}
find . -name 'pom.xml' | xargs git add

RELEASE_VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)

read -p "Finish release of ${RELEASE_VERSION} by pushing it to Git (y/n)?" choice
if [[ $choice == 'y' ]]; then
  mvn -q versions:commit
  git commit -m "release version ${RELEASE_VERSION}"
  echo "git push"
  git push

  echo "Have to wait until TravisCI finishes the release build, otherwise it will cancel it"
  read -p "Is TravisCI finished with the release build (https://travis-ci.org/ContainerSolutions/java-operator-sdk/) (y/n)?" finished
  if [[ $finished == 'y' ]]; then
    echo "Setting new SNAPSHOT version"
    mvn -q build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.nextIncrementalVersion}-SNAPSHOT versions:commit
    NEW_VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
    find . -name 'pom.xml' | xargs git add
    git commit -m "set SNAPSHOT version: ${NEW_VERSION}"
    echo "git push"
    git push
    echo "Finished release. New SNAPSHOT version is ${NEW_VERSION}"
  fi
else
  echo "Reverting version"
  mvn -q versions:revert
fi
