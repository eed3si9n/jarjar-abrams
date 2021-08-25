target(
  name='integration',
  dependencies=[
    ':java_versions',
    ':bad_packages',
  ],
)

java_library(
  name='base',
  sources=[
    'IntegrationTestBase.java',
    'FileTree.java',
    'ProcessCommunicator.java',
    'JarJarFailedException.java',
  ],
  dependencies=[
    'lib:asm',
    'lib:commons-lang3',
    'lib:junit',
  ],
)

junit_tests(
  name='java_versions',
  sources=['JavaVersionsTest.java'],
  test_platform='java8',
  dependencies=[':base'],
)

junit_tests(
  name='bad_packages',
  sources=['BadPackagesTest.java'],
  dependencies=[':base'],
)
