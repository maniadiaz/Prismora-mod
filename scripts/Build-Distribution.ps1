$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    & .\gradlew.bat build --console=plain
    if($LASTEXITCODE -ne 0){throw 'Build or tests failed'}
    $version='0.2.0'
    $dist=Join-Path $projectRoot 'dist'
    $staging=Join-Path $projectRoot ('.gradle/distribution/'+[guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Force $dist,$staging,(Join-Path $staging 'shaderpacks'),(Join-Path $staging 'docs') | Out-Null
    $jarName="prismora-$version.jar"
    Copy-Item -LiteralPath (Join-Path $projectRoot "build/libs/$jarName") -Destination (Join-Path $dist $jarName) -Force
    Copy-Item -LiteralPath (Join-Path $dist $jarName) -Destination $staging -Force
    foreach($name in @('Prismora-Color','Prismora-Bloom')){
        $packZip=Join-Path $dist "$name.zip"
        Compress-Archive -Path (Join-Path $projectRoot "examples/$name/*") -DestinationPath $packZip -Force
        Copy-Item -LiteralPath $packZip -Destination (Join-Path $staging 'shaderpacks') -Force
    }
    Copy-Item -LiteralPath README.md,CHANGELOG.md,LICENSE.md,PLANEACION.md -Destination $staging -Force
    Copy-Item -LiteralPath docs/FORMATO_PACKS.md,docs/IMPLEMENTACION_COMPATIBILIDAD.md,docs/VALIDACION.md,docs/GEOMETRIA.md,docs/PUBLICACION.md,docs/RENDIMIENTO.md,docs/PLAN_RENDIMIENTO.md,docs/PLAN_COMPATIBILIDAD_PROPUESTO.md,docs/compat-matrix.md,docs/BUG_REPORTS.md -Destination (Join-Path $staging 'docs') -Force
    Copy-Item -LiteralPath docs/planeacion -Destination (Join-Path $staging 'docs') -Recurse -Force
    Copy-Item -LiteralPath docs/pruebas -Destination (Join-Path $staging 'docs') -Recurse -Force
    Copy-Item -LiteralPath docs/screenshots -Destination (Join-Path $staging 'docs') -Recurse -Force
    Copy-Item -LiteralPath docs/third-party -Destination (Join-Path $staging 'docs') -Recurse -Force
    $bundle=Join-Path $dist "Prismora-$version.zip"
    Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $bundle -Force
    Get-ChildItem -LiteralPath $dist -File | Where-Object { $_.Extension -in '.jar','.zip' } | ForEach-Object {
        $hash=Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        $hash.Hash.ToLowerInvariant()+'  '+$_.Name
    } | Set-Content -Encoding ascii -LiteralPath (Join-Path $dist 'checksums.sha256')
    Write-Output "Distribution ready: $bundle"
} finally { Pop-Location }
