jarjar
==========

An export of https://code.google.com/p/jarjar/ @ svn:r142 for pants tool use and further
development.

releasing
=========

Requires membership in the Sonatype org.pantsbuild org to release.

+ Ensure all changes you want to release are committed and pushed to origin master.
+ Bump the jarjar.version in build.properties.
+ Run `./release.sh` (you will be prompted for a gpg passphrase).
+ Visit https://oss.sonatype.org/#stagingRepositories and search for pantsbuild to
  find the staging repository.  Verify its contents and then close the repo to start
  the Sonatype promotion machinery that syncs the repo to maven central.
 
