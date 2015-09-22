junit_tests(
  name='jarjar',
  sources=globs('*.java'),
  resources=[
    'src/test:generics-precompiled',
  ],
  dependencies=[
    'lib:asm',
    'lib:junit',
    'src/main/org/pantsbuild/jarjar',
    'src/test/org/pantsbuild/jarjar/integration',
    'src/test/org/pantsbuild/jarjar/example',
  ]
)

