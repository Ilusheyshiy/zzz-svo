# Авто-установщик «Капризного Бога» (Windows). Качает JDK 25, Gradle, Fabric installer, собирает мод и кладёт в mods.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root  = $PSScriptRoot
$tools = Join-Path $root 'tools'
$mc    = Join-Path $env:APPDATA '.minecraft'
$mcVer = '26.2'
New-Item -ItemType Directory -Force -Path $tools | Out-Null

function Step($t) { Write-Host "`n=== $t ===" -ForegroundColor Yellow }

# 1. JDK 25
Step 'JDK 25 (Temurin)'
$jdk = Get-ChildItem $tools -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdk) {
    $zip = Join-Path $tools 'jdk.zip'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    Remove-Item $zip
    $jdk = Get-ChildItem $tools -Directory -Filter 'jdk-25*' | Select-Object -First 1
}
$env:JAVA_HOME = $jdk.FullName
$env:PATH = "$($jdk.FullName)\bin;$env:PATH"
Write-Host "JDK: $($jdk.FullName)"

# 2. Gradle
Step 'Gradle 9.5.1'
$gradleDir = Join-Path $tools 'gradle-9.5.1'
if (-not (Test-Path $gradleDir)) {
    $zip = Join-Path $tools 'gradle.zip'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-9.5.1-bin.zip' -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    Remove-Item $zip
}

# 3. Сборка мода (Gradle сам подтянет Minecraft и библиотеки для компиляции — это нормально, это не установка в игру)
Step 'Сборка мода (первый раз 5-15 минут)'
Push-Location $root
& "$gradleDir\bin\gradle.bat" build --no-daemon
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'Сборка упала. Скопируй текст ошибки и отправь Клоду.' }
Pop-Location
$jar = Get-ChildItem (Join-Path $root 'build\libs') -Filter 'moody_god-*.jar' | Where-Object { $_.Name -notlike '*-sources*' } | Select-Object -First 1
if (-not $jar) { throw 'jar не найден в build\libs' }

# 4. Fabric Loader (через официальный installer)
Step 'Fabric Loader'
if (-not (Test-Path (Join-Path $mc 'launcher_profiles.json'))) {
    Write-Host 'ВНИМАНИЕ: не найден лаунчер Minecraft. Запусти официальный лаунчер один раз и повтори.' -ForegroundColor Red
}
$inst = (Invoke-RestMethod 'https://meta.fabricmc.net/v2/versions/installer') | Where-Object { $_.stable } | Select-Object -First 1
$instJar = Join-Path $tools 'fabric-installer.jar'
Invoke-WebRequest -UseBasicParsing -Uri $inst.url -OutFile $instJar
& "$($jdk.FullName)\bin\java.exe" -jar $instJar client -mcversion $mcVer -dir $mc
if ($LASTEXITCODE -ne 0) { throw 'Fabric installer упал. Отправь ошибку Клоду.' }

# 5. Копируем мод
Step 'Установка мода'
$mods = Join-Path $mc 'mods'
New-Item -ItemType Directory -Force -Path $mods | Out-Null
Copy-Item $jar.FullName $mods -Force
Write-Host "Мод скопирован в $mods" -ForegroundColor Green

Write-Host @"

ГОТОВО. Осталось вручную:
 1) Скачай Fabric API для $mcVer (файл fabric-api-0.161.0+26.2.jar или новее)
    https://modrinth.com/mod/fabric-api  и положи в папку: $mods
 2) Запусти лаунчер, выбери профиль 'fabric-loader-$mcVer', играй.
 3) Всё то же самое должен сделать твой друг. Тест: команда /god
"@
