java_library(
  name='jarjar',
  provides=artifact(
    org='org.pantsbuild',
    name='jarjar',
    repo=Repository(
      name='public',
      url='https://oss.sonatype.org/#stagingRepositories',
      push_db_basedir='build-support/ivy/pushdb'
    ),
    publication_metadata=ossrh(
      description='Jar Jar Links is a utility that makes it easy to repackage Java libraries and '
                  'embed them into your own distribution.',
      url='https://github.com/pantsbuild/jarjar',
      licenses=[
        license(
          name='Apache License, Version 2.0',
          url='http://www.apache.org/licenses/LICENSE-2.0'
        ),
      ],
      developers=[
        developer(
          name='The pants developers',
          email='pants-devel@googlegroups.com',
          url='https://github.com/pantsbuild/jarjar',
        ),
      ],
      scm=github(user='pantsbuild', repo='jarjar')
    )
  ),
  sources=rglobs('*.java'),
  resources=[
    ':help',
  ],
  dependencies=[
    'lib:ant',
    'lib:asm',
    'lib:asm-commons',
    'lib:maven-plugin-api',
  ]
)

resources(
  name='help',
  sources=['help.txt'],
)

jvm_binary(
  name='main',
  basename='jarjar',
  main='org.pantsbuild.jarjar.Main',
  dependencies=[
    ':jarjar',
  ]
)

