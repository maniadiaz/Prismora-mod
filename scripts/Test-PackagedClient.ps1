param([ValidateSet('opengl','vulkan')][string]$Backend='opengl',[ValidateSet('regression','performance','legacy')][string]$Probe='regression')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    & .\gradlew.bat build --console=plain
    if($LASTEXITCODE -ne 0){throw 'Build failed'}
    $isolated=Join-Path $projectRoot '.gradle/packaged-smoke'
    New-Item -ItemType Directory -Force (Join-Path $isolated 'run/mods'),(Join-Path $isolated 'run/shaderpacks') | Out-Null
    @'
plugins {
    id 'java'
    id 'net.minecraftforge.gradle' version '7.0.40'
}
java.toolchain.languageVersion = JavaLanguageVersion.of(25)
minecraft {
    runs {
        register('client') {
            workingDir = layout.projectDirectory.dir('run')
            systemProperty 'prismora.renderSmoke', providers.gradleProperty('prismoraProbe').map { it == 'regression' }.getOrElse('true')
            systemProperty 'prismora.legacyRenderSmoke', providers.gradleProperty('prismoraProbe').map { it == 'legacy' }.getOrElse('false')
            systemProperty 'prismora.performanceSmoke', providers.gradleProperty('prismoraProbe').map { it == 'performance' }.getOrElse('false')
            systemProperty 'prismora.smokePack', 'Prismora-Multipass'
            systemProperty 'prismora.expectedStartup', 'Prismora-Bloom'
        }
    }
}
repositories {
    minecraft.mavenizer(it)
    maven fg.forgeMaven
    maven fg.minecraftLibsMaven
    mavenCentral()
}
dependencies { implementation minecraft.dependency('net.minecraftforge:forge:26.3-66.1.0') }
'@ | Set-Content -Encoding utf8 -LiteralPath (Join-Path $isolated 'build.gradle')
    "rootProject.name = 'prismora-packaged-verification'" | Set-Content -Encoding utf8 -LiteralPath (Join-Path $isolated 'settings.gradle')
    Copy-Item -LiteralPath build/libs/prismora-0.2.0.jar -Destination (Join-Path $isolated 'run/mods/prismora-0.2.0.jar') -Force
    # This directory is an isolated test fixture; remove only older Prismora build artifacts.
    Get-ChildItem -LiteralPath (Join-Path $isolated 'run/mods') -Filter 'prismora-*.jar' -File | Where-Object Name -ne 'prismora-0.2.0.jar' | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
    Get-ChildItem -LiteralPath examples -Directory | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $isolated 'run/shaderpacks') -Recurse -Force }
    if($Probe -eq 'legacy'){Copy-Item -LiteralPath src/test/resources/legacy/PostFixture -Destination (Join-Path $isolated 'run/shaderpacks') -Recurse -Force}
    $options=Join-Path $isolated 'run/options.txt'
    $lines=if(Test-Path -LiteralPath $options){@(Get-Content -LiteralPath $options | Where-Object {$_ -notlike 'preferredGraphicsBackend:*'})}else{@('version:5023')}
    $text=(@($lines; 'preferredGraphicsBackend:"'+$Backend+'"') -join [Environment]::NewLine)+[Environment]::NewLine
    [IO.File]::WriteAllText($options,$text,[Text.UTF8Encoding]::new($false))
    New-Item -ItemType Directory -Force (Join-Path $isolated 'run/config') | Out-Null
    [IO.File]::WriteAllText((Join-Path $isolated 'run/config/prismora.json'),'{"selected":"Prismora-Bloom","options":{"Prismora-Bloom":{"bloom_strength":0.15}}}',[Text.UTF8Encoding]::new($false))
    & .\gradlew.bat -p $isolated runClient "-PprismoraProbe=$Probe" --console=plain
    if($LASTEXITCODE -ne 0){throw 'Packaged client failed'}
    $log=Get-Content -Raw -LiteralPath (Join-Path $isolated 'run/logs/latest.log')
    $markers=if($Probe -eq 'legacy'){@('PRISMORA_LEGACY_GPU_PASS','PRISMORA_LEGACY_DEPTH_PASS','PRISMORA_LEGACY_OPTIONS_PASS','PRISMORA_LEGACY_SOURCE_ERROR_PASS','PRISMORA_LEGACY_NETHER_PASS','PRISMORA_LEGACY_DIMENSION_PASS','PRISMORA_LEGACY_RENDER_SMOKE_PASS')}elseif($Probe -eq 'performance'){@('PRISMORA_SCALED_TARGETS_PASS','PRISMORA_ENTRY_PROFILE_PASS','PRISMORA_PERFORMANCE_SMOKE_PASS','PRISMORA_ADAPTIVE_FALLBACK_PASS','PRISMORA_OPTION_WIDGETS_PASS','PRISMORA_PROFILE_BENCHMARK_PASS')}else{@('PRISMORA_GPU_PASS','PRISMORA_RENDER_SMOKE_PASS','PRISMORA_ERROR_RECOVERY_PASS','PRISMORA_BLOOM_PASS','PRISMORA_OPTIONS_PASS','PRISMORA_HOT_RELOAD_PASS','PRISMORA_RESTORE_PASS','PRISMORA_RESIZE_PASS')}
    foreach($marker in $markers){
        if(-not $log.Contains($marker)){throw "Missing packaged validation marker: $marker"}
    }
    if($log -notmatch "Using graphics backend $Backend"){throw 'Backend fallback detected'}
    Write-Output "Packaged Prismora JAR verified on $Backend"
} finally { Pop-Location }
