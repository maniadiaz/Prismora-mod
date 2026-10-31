param([ValidateSet('opengl','vulkan')][string]$Backend='opengl')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    & .\gradlew.bat build --console=plain
    if($LASTEXITCODE -ne 0){throw 'Build or unit tests failed'}
    New-Item -ItemType Directory -Force run/shaderpacks | Out-Null
    Copy-Item -LiteralPath examples/Prismora-Color,examples/Prismora-Diagnostic,examples/Prismora-Multipass,examples/Prismora-Bloom -Destination run/shaderpacks -Recurse -Force
    $options=Join-Path $projectRoot 'run/options.txt'
    $lines=if(Test-Path -LiteralPath $options){Get-Content -LiteralPath $options | Where-Object {$_ -notlike 'preferredGraphicsBackend:*'}}else{@('version:5023')}
    $text=(@($lines; 'preferredGraphicsBackend:"'+$Backend+'"') -join [Environment]::NewLine)+[Environment]::NewLine
    [IO.File]::WriteAllText($options,$text,[Text.UTF8Encoding]::new($false))
    New-Item -ItemType Directory -Force run/config | Out-Null
    [IO.File]::WriteAllText((Join-Path $projectRoot 'run/config/prismora.json'),'{"selected":"Prismora-Bloom","options":{"Prismora-Bloom":{"bloom_strength":0.15}}}',[Text.UTF8Encoding]::new($false))
    & .\gradlew.bat runClient -PprismoraRenderSmoke=true -PprismoraSmokePack=Prismora-Multipass -PprismoraExpectedStartup=Prismora-Bloom --console=plain
    if($LASTEXITCODE -ne 0){throw 'Render smoke failed'}
    $log=Get-Content -Raw -LiteralPath run/logs/latest.log
    foreach($marker in @('PRISMORA_GPU_PASS','PRISMORA_RENDER_SMOKE_PASS','PRISMORA_ERROR_RECOVERY_PASS','PRISMORA_BLOOM_PASS','PRISMORA_OPTIONS_PASS','PRISMORA_HOT_RELOAD_PASS','PRISMORA_RESTORE_PASS','PRISMORA_RESIZE_PASS')){
        if(-not $log.Contains($marker)){throw "Missing validation marker: $marker"}
    }
    if($log -notmatch "Using graphics backend $Backend") { throw "Requested backend $Backend was not used (possible fallback)" }
    Write-Output "Prismora verification passed on $Backend"
} finally { Pop-Location }
