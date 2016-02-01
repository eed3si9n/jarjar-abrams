junit_tests(
  name='jarjar',
  sources=globs('*.java'),
  resources=[
    'src/test:generics-precompiled',
  ],
  dependencies=[
    'lib:asm',
    'lib:junit',
    'src/main/java/org/pantsbuild/jarjar',
    'src/test/java/org/pantsbuild/jarjar/integration',
    'src/test/java/org/pantsbuild/jarjar/example',
  ]
)

