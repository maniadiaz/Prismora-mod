param([ValidateSet('opengl','vulkan')][string]$Backend='opengl',[ValidateSet('regression','performance','legacy','mrt','terrain')][string]$Probe='regression',[string]$AdditionalModsDirectory='',[string]$ComplementaryPack='',[switch]$FreshWorld)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    & .\gradlew.bat build --console=plain
    if($LASTEXITCODE -ne 0){throw 'Build failed'}
    $isolated=Join-Path $projectRoot '.gradle/packaged-smoke'
    $packPath=''
    if($ComplementaryPack){
        if($Probe -ne 'terrain'){throw '-ComplementaryPack requires -Probe terrain'}
        $packPath=(Resolve-Path -LiteralPath $ComplementaryPack).Path
    }
    $additionalMods=@()
    if($AdditionalModsDirectory){
        $additionalMods=@(Get-ChildItem -LiteralPath $AdditionalModsDirectory -Filter '*.jar' -File | Where-Object Name -notlike 'prismora-*.jar' | Sort-Object Name)
        if(-not $additionalMods.Count){throw 'The additional mods directory has no JAR files'}
        $inventory=@($additionalMods | ForEach-Object {[ordered]@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}})
        $inventoryJson=ConvertTo-Json -InputObject $inventory -Depth 3
        $digest=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($inventoryJson))).Substring(0,12)
        $isolated=Join-Path $projectRoot ".gradle/packaged-compat-$digest"
    }
    if($Probe -eq 'terrain'){$isolated += "-terrain-$Backend"}
    New-Item -ItemType Directory -Force (Join-Path $isolated 'run/mods'),(Join-Path $isolated 'run/shaderpacks') | Out-Null
    if($additionalMods.Count){
        foreach($mod in $additionalMods){Copy-Item -LiteralPath $mod.FullName -Destination (Join-Path $isolated 'run/mods') -Force}
        [IO.File]::WriteAllText((Join-Path $isolated 'mods-inventory.json'),$inventoryJson,[Text.UTF8Encoding]::new($false))
        Write-Output "Testing isolated copies of $($additionalMods.Count) additional mods in $isolated"
    }
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
            systemProperty 'prismora.smokeFreshWorld', providers.gradleProperty('prismoraFreshWorld').getOrElse('false')
            systemProperty 'prismora.terrainPack', providers.gradleProperty('prismoraTerrainPack').getOrElse('')
            systemProperty 'prismora.terrainSmoke', providers.gradleProperty('prismoraProbe').map { it == 'terrain' }.getOrElse('false')
            systemProperty 'prismora.mrtSmoke', providers.gradleProperty('prismoraProbe').map { it == 'mrt' }.getOrElse('false')
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
    & .\gradlew.bat -p $isolated runClient "-PprismoraProbe=$Probe" "-PprismoraTerrainPack=$packPath" "-PprismoraFreshWorld=$($FreshWorld.IsPresent -or [bool]$additionalMods.Count)" --console=plain
    if($LASTEXITCODE -ne 0){throw 'Packaged client failed'}
    $log=Get-Content -Raw -LiteralPath (Join-Path $isolated 'run/logs/latest.log')
    $markers=if($Probe -eq 'terrain'){@('PRISMORA_WORLD_TERRAIN_MRT_PASS')}elseif($Probe -eq 'mrt'){@('PRISMORA_MRT_HDR_SNORM_PINGPONG_PASS','PRISMORA_MRT_PERSISTENCE_CLEAR_PASS','PRISMORA_MRT_RESIZE_TRANSACTION_PASS','PRISMORA_MRT_FORMATS_PASS','PRISMORA_MRT_DEPTH_SNAPSHOTS_PASS','PRISMORA_MRT_SMOKE_PASS')}elseif($Probe -eq 'legacy'){@('PRISMORA_LEGACY_GPU_PASS','PRISMORA_LEGACY_DEPTH_PASS','PRISMORA_LEGACY_MATRICES_PASS','PRISMORA_LEGACY_OPTIONS_PASS','PRISMORA_LEGACY_SOURCE_ERROR_PASS','PRISMORA_LEGACY_NETHER_PASS','PRISMORA_LEGACY_DIMENSION_PASS','PRISMORA_LEGACY_RENDER_SMOKE_PASS')}elseif($Probe -eq 'performance'){@('PRISMORA_SCALED_TARGETS_PASS','PRISMORA_ENTRY_PROFILE_PASS','PRISMORA_PERFORMANCE_SMOKE_PASS','PRISMORA_ADAPTIVE_FALLBACK_PASS','PRISMORA_OPTION_WIDGETS_PASS','PRISMORA_PROFILE_BENCHMARK_PASS')}else{@('PRISMORA_GPU_PASS','PRISMORA_RENDER_SMOKE_PASS','PRISMORA_ERROR_RECOVERY_PASS','PRISMORA_BLOOM_PASS','PRISMORA_OPTIONS_PASS','PRISMORA_HOT_RELOAD_PASS','PRISMORA_RESTORE_PASS','PRISMORA_RESIZE_PASS')}
    if($packPath){$markers=@($markers)+'PRISMORA_WORLD_TERRAIN_MATERIAL_PASS','PRISMORA_WORLD_TERRAIN_VERTEX_PASS','PRISMORA_WORLD_TERRAIN_CAMERA_PASS','PRISMORA_COMPLEMENTARY_TERRAIN_COMPILE_PASS','PRISMORA_WORLD_TERRAIN_UNIFORMS_PASS','PRISMORA_COMPLEMENTARY_SHADOW_COMPILE_PASS','PRISMORA_WORLD_SHADOW_COMPARE_PASS','PRISMORA_WORLD_SHADOW_GEOMETRY_PASS','PRISMORA_COMPLEMENTARY_SHADOW_DRAW_PASS','PRISMORA_COMPLEMENTARY_TERRAIN_DRAW_PASS','PRISMORA_COMPLEMENTARY_POST_COMPILE_PASS','PRISMORA_COMPLEMENTARY_POST_DRAW_PASS','PRISMORA_COMPLEMENTARY_PRESENT_PASS','PRISMORA_COMPLEMENTARY_PRESENT_CAPTURE','PRISMORA_COMPLEMENTARY_LIVE_SEQUENCE_PASS','PRISMORA_COMPLEMENTARY_MULTISECTION_PASS','PRISMORA_COMPLEMENTARY_POSE_HISTORY_PASS','PRISMORA_COMPLEMENTARY_VISIBLE_TERRAIN_PASS','PRISMORA_COMPLEMENTARY_SECTION_PLACE_PASS','PRISMORA_COMPLEMENTARY_SECTION_BREAK_PASS','PRISMORA_COMPLEMENTARY_SECTION_UNLOAD_PASS','PRISMORA_COMPLEMENTARY_SECTION_LOAD_PASS','PRISMORA_COMPLEMENTARY_FRAME_REUSE_PASS','PRISMORA_COMPLEMENTARY_FRAME_RESIZE_PASS','PRISMORA_COMPLEMENTARY_WORLD_RELEASE_PASS','PRISMORA_COMPLEMENTARY_SKY_COMPILE_PASS','PRISMORA_COMPLEMENTARY_SKY_DRAW_PASS'}
    foreach($marker in $markers){
        if(-not $log.Contains($marker)){throw "Missing packaged validation marker: $marker"}
    }
    if ($packPath -and [regex]::Matches($log, 'PRISMORA_COMPLEMENTARY_WORLD_FRAME_PASS: pose [1-3] submitted').Count -ne 3) {
        throw 'Expected all three Complementary poses to submit from the world render callback'
    }
    if($log -notmatch "Using graphics backend $Backend"){throw 'Backend fallback detected'}
    Write-Output "Packaged Prismora JAR verified on $Backend"
} finally { Pop-Location }
