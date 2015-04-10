#!/usr/bin/env bash
# Copyright 2015 Pants project contributors (see CONTRIBUTORS.md).
# Licensed under the Apache License, Version 2.0 (see LICENSE).

REPO_ROOT=$(cd $(dirname "${BASH_SOURCE[0]}") && cd "$(git rev-parse --show-toplevel)" && pwd)
cd ${REPO_ROOT}

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

ant stage

