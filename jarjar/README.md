jarjar
==========

An export of https://code.google.com/p/jarjar/ @ svn:r142 for pants tool use and further
development.

We use [Travis CI](https://travis-ci.org) to verify the build
[![Build Status](https://travis-ci.org/pantsbuild/jarjar.svg?branch=master)](https://travis-ci.org/pantsbuild/jarjar)

releasing
=========

Requires membership in the Sonatype org.pantsbuild org to release.

+ Ensure all changes you want to release are committed and pushed to origin master.
+ Bump the jarjar.version in build.properties.
+ Run `./release.sh` which will prompt for a gpg passphase and then walk you through
  the maven staging, close or drop, and finally synced to maven central process.

