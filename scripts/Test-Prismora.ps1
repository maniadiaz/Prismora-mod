param([ValidateSet('opengl','vulkan')][string]$Backend='opengl')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    & .\gradlew.bat build --console=plain
    if($LASTEXITCODE -ne 0){throw 'Build or unit tests failed'}
    New-Item -ItemType Directory -Force run/shaderpacks | Out-Null
    Copy-Item -LiteralPath examples/Prismora-Color,examples/Prismora-Diagnostic -Destination run/shaderpacks -Recurse -Force
    $options=Join-Path $projectRoot 'run/options.txt'
    $lines=if(Test-Path -LiteralPath $options){Get-Content -LiteralPath $options | Where-Object {$_ -notlike 'preferredGraphicsBackend:*'}}else{@()}
    @($lines; 'preferredGraphicsBackend:"'+$Backend+'"') | Set-Content -Encoding utf8 -LiteralPath $options
    & .\gradlew.bat runClient -PprismoraRenderSmoke=true --console=plain
    if($LASTEXITCODE -ne 0){throw 'Render smoke failed'}
    $log=Get-Content -Raw -LiteralPath run/logs/latest.log
    foreach($marker in @('PRISMORA_GPU_PASS','PRISMORA_RENDER_SMOKE_PASS','PRISMORA_ERROR_RECOVERY_PASS')){
        if(-not $log.Contains($marker)){throw "Missing validation marker: $marker"}
    }
    if($log -notmatch "Using graphics backend $Backend") { throw "Requested backend $Backend was not used (possible fallback)" }
    Write-Output "Prismora verification passed on $Backend"
} finally { Pop-Location }
