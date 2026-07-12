# Construit le bundle Maven Central (jars + javadoc + signatures GPG + checksums)
# Sortie : target\central-bundle.zip — a televerser sur https://central.sonatype.com
# Prerequis : JDK 17, gpg avec la cle de signature, lib\ rempli par build-and-test.ps1

$ErrorActionPreference = "Stop"

$Jdk        = "C:\Program Files\Java\jdk-17\bin"
$GroupPath  = "io\github\konatem-mk9"
$ArtifactId = "fameen-messaging"
$Version    = "0.1.0"
$GpgKey     = "46F05D46568796296220FCB386D40718F9BDD386"

$Root = $PSScriptRoot
$Cp   = "$Root\lib\jackson-core-2.17.2.jar;$Root\lib\jackson-databind-2.17.2.jar;$Root\lib\jackson-annotations-2.17.2.jar"

# gpg : livre avec Git pour Windows (absent du PATH PowerShell)
$Gpg = "C:\Program Files\Git\usr\bin\gpg.exe"
if (-not (Test-Path $Gpg)) {
  $cmd = Get-Command gpg -ErrorAction SilentlyContinue
  if ($null -eq $cmd) { throw "gpg introuvable - installez GnuPG ou Git pour Windows" }
  $Gpg = $cmd.Source
}

if (-not (Test-Path "$Root\lib\jackson-databind-2.17.2.jar")) {
  throw "lib\ incomplet - lancez d'abord .\build-and-test.ps1 pour telecharger les jars"
}

# ── 1. Compilation propre ────────────────────────────────────────────────────
Write-Host "[1/5] Compilation..."
if (Test-Path "$Root\out\central") { Remove-Item -Recurse -Force "$Root\out\central" }
New-Item -ItemType Directory -Force -Path "$Root\out\central\classes" | Out-Null
$sources = Get-ChildItem -Recurse -Filter *.java -Path "$Root\src\main\java" | ForEach-Object { $_.FullName }
& "$Jdk\javac.exe" -encoding UTF-8 --release 17 -cp $Cp -d "$Root\out\central\classes" @sources
if ($LASTEXITCODE -ne 0) { throw "javac a echoue" }

# ── 2. Javadoc ───────────────────────────────────────────────────────────────
Write-Host "[2/5] Javadoc..."
& "$Jdk\javadoc.exe" -encoding UTF-8 -charset UTF-8 -docencoding UTF-8 -Xdoclint:none -quiet `
  -cp $Cp -d "$Root\out\central\javadoc" @sources
if ($LASTEXITCODE -ne 0) { throw "javadoc a echoue" }

# ── 3. Jars + pom ────────────────────────────────────────────────────────────
Write-Host "[3/5] Jars..."
$Dist = "$Root\target\central-bundle\$GroupPath\$ArtifactId\$Version"
if (Test-Path "$Root\target") { Remove-Item -Recurse -Force "$Root\target" }
New-Item -ItemType Directory -Force -Path $Dist | Out-Null

$Base = "$Dist\$ArtifactId-$Version"
& "$Jdk\jar.exe" --create --file "$Base.jar" -C "$Root\out\central\classes" .
if ($LASTEXITCODE -ne 0) { throw "jar (classes) a echoue" }
& "$Jdk\jar.exe" --create --file "$Base-sources.jar" -C "$Root\src\main\java" .
if ($LASTEXITCODE -ne 0) { throw "jar (sources) a echoue" }
& "$Jdk\jar.exe" --create --file "$Base-javadoc.jar" -C "$Root\out\central\javadoc" .
if ($LASTEXITCODE -ne 0) { throw "jar (javadoc) a echoue" }
Copy-Item "$Root\pom.xml" "$Base.pom"

# ── 4. Signatures GPG + checksums ────────────────────────────────────────────
Write-Host "[4/5] Signatures et checksums..."
$files = @("$Base.pom", "$Base.jar", "$Base-sources.jar", "$Base-javadoc.jar")
foreach ($f in $files) {
  & $Gpg --batch --yes --armor --detach-sign --local-user $GpgKey $f
  if ($LASTEXITCODE -ne 0) { throw "gpg a echoue sur $f" }
  $md5  = (Get-FileHash -Algorithm MD5  $f).Hash.ToLower()
  $sha1 = (Get-FileHash -Algorithm SHA1 $f).Hash.ToLower()
  [IO.File]::WriteAllText("$f.md5",  $md5)
  [IO.File]::WriteAllText("$f.sha1", $sha1)
}

# ── 5. Zip (via jar.exe : entrees zip a slashs corrects, sans manifest) ─────
Write-Host "[5/5] Bundle zip..."
& "$Jdk\jar.exe" --create --no-manifest --file "$Root\target\central-bundle.zip" -C "$Root\target\central-bundle" .
if ($LASTEXITCODE -ne 0) { throw "creation du zip a echoue" }

Write-Host ""
Write-Host "SUCCES : $Root\target\central-bundle.zip"
Write-Host "Contenu :"
& "$Jdk\jar.exe" --list --file "$Root\target\central-bundle.zip"
