# ============================================================================
# Compile et teste le SDK Java Fameen Messaging SANS Maven.
# Prerequis : JDK 17+ (java + javac) et curl dans le PATH.
# Usage : powershell -ExecutionPolicy Bypass -File .\build-and-test.ps1
# ============================================================================

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$jacksonVersion = "2.17.2"
$junitVersion = "1.10.2"
$mavenRepo = "https://repo1.maven.org/maven2"

# ----------------------------------------------------------------------------
# 1. Telechargement des jars manquants dans lib\ (idempotent)
# ----------------------------------------------------------------------------
$jars = @(
    @{ Name = "jackson-core-$jacksonVersion.jar";
       Url  = "$mavenRepo/com/fasterxml/jackson/core/jackson-core/$jacksonVersion/jackson-core-$jacksonVersion.jar" },
    @{ Name = "jackson-databind-$jacksonVersion.jar";
       Url  = "$mavenRepo/com/fasterxml/jackson/core/jackson-databind/$jacksonVersion/jackson-databind-$jacksonVersion.jar" },
    @{ Name = "jackson-annotations-$jacksonVersion.jar";
       Url  = "$mavenRepo/com/fasterxml/jackson/core/jackson-annotations/$jacksonVersion/jackson-annotations-$jacksonVersion.jar" },
    @{ Name = "junit-platform-console-standalone-$junitVersion.jar";
       Url  = "$mavenRepo/org/junit/platform/junit-platform-console-standalone/$junitVersion/junit-platform-console-standalone-$junitVersion.jar" }
)

if (-not (Test-Path "$root\lib")) {
    New-Item -ItemType Directory -Path "$root\lib" | Out-Null
}

foreach ($jar in $jars) {
    $target = Join-Path "$root\lib" $jar.Name
    if (-not (Test-Path $target)) {
        Write-Host "Telechargement de $($jar.Name)..."
        & curl.exe -fsSL -o $target $jar.Url
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ECHEC du telechargement de $($jar.Url)"
            exit 1
        }
    }
}

# ----------------------------------------------------------------------------
# 2. Compilation
# ----------------------------------------------------------------------------
if (Test-Path "$root\out") {
    Remove-Item -Recurse -Force "$root\out"
}
New-Item -ItemType Directory -Path "$root\out\main" | Out-Null
New-Item -ItemType Directory -Path "$root\out\test" | Out-Null

Write-Host ""
Write-Host "Compilation de src/main/java vers out/main..."
$mainSources = Get-ChildItem -Path "$root\src\main\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 --release 17 -cp "lib/*" -d "$root\out\main" $mainSources
if ($LASTEXITCODE -ne 0) {
    Write-Host "ECHEC de la compilation (main)"
    exit 1
}

Write-Host "Compilation de src/test/java vers out/test..."
$testSources = Get-ChildItem -Path "$root\src\test\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 --release 17 -cp "out/main;lib/*" -d "$root\out\test" $testSources
if ($LASTEXITCODE -ne 0) {
    Write-Host "ECHEC de la compilation (test)"
    exit 1
}

# ----------------------------------------------------------------------------
# 3. Tests JUnit (console standalone)
# ----------------------------------------------------------------------------
Write-Host "Lancement des tests JUnit..."
& java -jar "lib/junit-platform-console-standalone-$junitVersion.jar" execute --class-path "out/main;out/test;lib/jackson-core-$jacksonVersion.jar;lib/jackson-databind-$jacksonVersion.jar;lib/jackson-annotations-$jacksonVersion.jar" --scan-class-path
$testExit = $LASTEXITCODE
if ($testExit -ne 0) {
    Write-Host "ECHEC des tests (code $testExit)"
    exit $testExit
}

Write-Host ""
Write-Host "SUCCES : compilation et tests OK."
exit 0
