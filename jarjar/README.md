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
+ ./pants publish --no-publish-jar-dryrun src/main/java/org/pantsbuild/jarjar:
