param([Parameter(ValueFromRemainingArguments=$true)][string[]]$ToolArgs)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $repo 'build/p8-offline'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac' }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$src = Join-Path $repo 'app/src/main/java/com/macdmr/radio'
& $javac -d $classes (Join-Path $src 'Frame.java') (Join-Path $src 'P8Protocol.java') (Join-Path $src 'PcmFramer.java') (Join-Path $PSScriptRoot 'P8Offline.java')
if ($LASTEXITCODE -ne 0) { throw 'Offline utility compilation failed' }
& $java -cp $classes P8Offline @ToolArgs
exit $LASTEXITCODE
