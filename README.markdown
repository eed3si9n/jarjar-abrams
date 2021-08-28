Jar Jar Abrams
==============

[![Continuous Integration](https://github.com/eed3si9n/jarjar-abrams/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/eed3si9n/jarjar-abrams/actions/workflows/ci.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.eed3si9n.jarjarabrams/jarjar-abrams-core_2.13/badge.svg)](https://search.maven.org/search?q=g:com.eed3si9n.jarjarabrams)

Jar Jar Abrams is an experimental Scala extension of [Jar Jar Links][links] a utility to shade Java libraries.

Jar Jar Links
============

In 2004, herbyderby (Chris Nokleberg) created a tool called Jar Jar Links that can repackage Java libraries.

Now defunct [fork of Jar Jar Links by Pants](https://github.com/pantsbuild/jarjar) was in-sourced at commit 57845dc73d3e2c9b916ae4a788cfa12114fd7df1, dated Oct 28, 2018.

## License

Licensed under the Apache License, Version 2.0.

## Credits

- [Jar Jar Links][links] was created by herbyderby (Chris Nokleberg) in 2004.
- Pants build team has been maintaining a fork [pantsbuild/jarjar][pj] since 2015.
- In 2015, Wu Xiang added shading support in [sbt-assembly#162](https://github.com/sbt/sbt-assembly/pull/162).
- In 2020, Jeroen ter Voorde added Scala signature processor in [sbt-assembly#393](https://github.com/sbt/sbt-assembly/pull/393).

  [links]: https://code.google.com/archive/p/jarjar/
  [pj]: https://github.com/pantsbuild/jarjar
