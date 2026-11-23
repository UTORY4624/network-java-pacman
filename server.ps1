param(
    [ValidateSet('build', 'test', 'test-ui', 'run', 'connect')][string]$Task = 'run',
    [int]$Port = 5555
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # 서버를 켜둔 채 접속 창이나 테스트를 실행해도 빌드 파일이 겹치지 않게 하기
    $folder = 'server'
    if ($Task -eq 'connect') { $folder = 'client' }
    if ($Task -eq 'test' -or $Task -eq 'test-ui') { $folder = 'tests' }
    $buildPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "build/$folder"))
    $projectPath = [System.IO.Path]::GetFullPath($PSScriptRoot) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $buildPath.StartsWith($projectPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw '빌드 폴더 경로를 확인해주세요.'
    }
    if (Test-Path -LiteralPath $buildPath) {
        Remove-Item -LiteralPath $buildPath -Recurse -Force
    }
    $classes = Join-Path $buildPath 'classes'
    $jarPath = Join-Path $buildPath 'pacman-server.jar'
    $javaOptions = @('-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8')
    New-Item -ItemType Directory -Force $classes | Out-Null
    $sources = @(Get-ChildItem 'src/java/pacman' -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac -encoding UTF-8 -source 8 -target 8 -d $classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed. Use a JDK with javac.' }
    # Eclipse처럼 실행 파일에도 맵을 넣어서 어느 폴더에서 실행해도 읽히게 하기
    New-Item -ItemType Directory -Force (Join-Path $classes 'level') | Out-Null
    Copy-Item -LiteralPath 'src/resources/level/level.csv' -Destination (Join-Path $classes 'level/level.csv')
    if ($Task -eq 'build' -or $Task -eq 'run') {
        & jar cfe $jarPath pacman.server.PacmanServer -C $classes .
        if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed.' }
    }
    if ($Task -eq 'test' -or $Task -eq 'test-ui') {
        $testClasses = Join-Path $buildPath 'test-classes'
        $testClasspath = $classes + [System.IO.Path]::PathSeparator + $testClasses
        New-Item -ItemType Directory -Force $testClasses | Out-Null
        $tests = @(Get-ChildItem 'src/test/java' -Recurse -Filter '*.java' | ForEach-Object FullName)
        & javac -encoding UTF-8 -source 8 -target 8 -cp $classes -d $testClasses $tests
        if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
        if ($Task -eq 'test') {
            & java @javaOptions '-Djava.awt.headless=true' -ea -cp $testClasspath pacman.server.GameStateTests
            if ($LASTEXITCODE -ne 0) { throw 'Game state tests failed.' }
            & java @javaOptions -ea -cp $testClasspath pacman.server.ServerTests
            if ($LASTEXITCODE -ne 0) { throw 'Server tests failed.' }
            & java @javaOptions -ea -cp $testClasspath pacman.client.ConnectionTests
            if ($LASTEXITCODE -ne 0) { throw 'Client connection tests failed.' }
        } else {
            & java @javaOptions -ea -cp $testClasspath pacman.client.ConnectionWindowTests
            if ($LASTEXITCODE -ne 0) { throw 'Connection window tests failed.' }
            & java @javaOptions -ea -cp $testClasspath pacman.client.LobbyWindowTests
            if ($LASTEXITCODE -ne 0) { throw 'Lobby window tests failed.' }
        }
    }
    if ($Task -eq 'run') {
        & java @javaOptions -jar $jarPath $Port
        if ($LASTEXITCODE -ne 0) { throw 'Server stopped with an error.' }
    }
    if ($Task -eq 'build') { Write-Output 'Built build/server/pacman-server.jar' }
    if ($Task -eq 'connect') {
        & java @javaOptions -cp $classes pacman.client.ConnectionWindow
        if ($LASTEXITCODE -ne 0) { throw 'Connection window stopped with an error.' }
    }
}
finally { Pop-Location }
