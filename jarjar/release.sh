#!/usr/bin/env bash
# Copyright 2015 Pants project contributors (see CONTRIBUTORS.md).
# Licensed under the Apache License, Version 2.0 (see LICENSE).

REPO_ROOT=$(cd $(dirname "${BASH_SOURCE[0]}") && cd "$(git rev-parse --show-toplevel)" && pwd)
cd ${REPO_ROOT}

function die() {
  echo "$@"
  exit 1
}

function local_version() {
  grep jarjar.version build.properties | cut -d= -f2
}

function check_clean_master() {
  [[
    -z "$(git status --porcelain)" &&
    "$(git branch | grep -E '^* ' | cut -d' ' -f2-)" == "master"
  ]] || die "You are not on a clean master branch."
}

function tag_release() {
  release_version="$(local_version)" && \
  tag_name="release_${release_version}" && \
  git tag --sign -m "pantsbuild.jarjar release ${release_version}" ${tag_name} && \
    git push git@github.com:pantsbuild/jarjar.git ${tag_name}
}

function usage() {
  echo "Stages jarjar production releases to maven central."
  echo
  echo "The script will build the release and then prompt you for a gpg"
  echo "passphrase before uploading the artifacts to the maven central"
  echo "staging repo and tagging the release."
  echo
  echo "NB: You must visit https://oss.sonatype.org/#stagingRepositories"
  echo "and search for 'pantsbuild' to find the staged repo.  After you"
  echo "find it, verify its contents and close the repo to trigger the"
  echo "propagation to maven central."
  echo
  echo "Usage:"
  echo "  $0 [-h]"
  echo  
  echo " -h  Prints out this help message."
  exit 0
}

while getopts "h" opt; do
  case ${opt} in
    h) usage ;;
    *) usage "Invalid option: -${OPTARG}" ;;
  esac
done

check_clean_master
ant stage || die "Failed to stage the release to https://oss.sonatype.org/#stagingRepositories"

echo "The release has been staged at https://oss.sonatype.org/#stagingRepositories"
echo
echo "Please visit https://oss.sonatype.org/#stagingRepositories and search for"
echo "'pantsbuild' to find the repo you just staged.  Once found, verify its"
echo "contents.  If everything looks good, close the repo and answer 'y' to tag"
echo "this release.  If things aren't right, instead drop the repo and answer 'n'."
echo
read -p "Tag the release? [yN]:" tag && \
[[ "${tag/Y/y}" == "y" ]] || \
  die "Release aborted.  Please drop the staging repo on sonatype."

tag_release || die "Failed to tag the release."

