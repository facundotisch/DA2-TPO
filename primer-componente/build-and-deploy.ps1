# Script de compilacion y despliegue para StayHub - ServicioDeHoteles
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = $scriptDir

# Rutas del entorno
$jdkBin = "C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot\bin"
if (!(Test-Path "$jdkBin\javac.exe")) {
    $jdkHome = [System.Environment]::GetEnvironmentVariable("JAVA_HOME")
    if ($jdkHome -and (Test-Path "$jdkHome\bin\javac.exe")) {
        $jdkBin = "$jdkHome\bin"
    } else {
        throw "No se encontro javac.exe en $jdkBin ni en JAVA_HOME"
    }
}

$wildflyDir = Resolve-Path "$projectDir\..\wildfly-41.0.0.Final" -ErrorAction SilentlyContinue
if (!$wildflyDir -or !(Test-Path "$wildflyDir\bin\standalone.bat")) {
    $wildflyDir = "C:\wildfly"
}

Write-Host ">> Compilando con JDK en: $jdkBin" -ForegroundColor Cyan
Write-Host ">> Destino WildFly: $wildflyDir" -ForegroundColor Cyan

# Preparar carpetas
$targetDir = "$projectDir\target"
$classesDir = "$targetDir\classes"
$warStaging = "$targetDir\war-staging"

if (!(Test-Path $classesDir)) { New-Item -ItemType Directory -Path $classesDir -Force | Out-Null }
if (Test-Path $warStaging) { Remove-Item -Recurse -Force $warStaging }
New-Item -ItemType Directory -Path "$warStaging\WEB-INF\classes" -Force | Out-Null

# Jars de Jakarta EE para compilar
$apiJars = Get-ChildItem "$wildflyDir\modules" -Filter "jakarta*.jar" -Recurse | Select-Object -ExpandProperty FullName
$cpArg = '"' + ($apiJars -join '";"') + '"'

# Lista de fuentes Java (con barras / para javac)
$sources = Get-ChildItem "$projectDir\src\main\java" -Filter "*.java" -Recurse | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
$sourcesFile = "$targetDir\sources.txt"
$sources | Out-File -FilePath $sourcesFile -Encoding ascii

Write-Host ">> Compilando fuentes Java..." -ForegroundColor Yellow
& "$jdkBin\javac.exe" -encoding UTF-8 -cp $cpArg -d "$classesDir" "@$sourcesFile"
if ($LASTEXITCODE -ne 0) {
    throw "Error durante la compilacion con javac"
}

Write-Host ">> Empaquetando WAR..." -ForegroundColor Yellow
Copy-Item -Path "$classesDir\*" -Destination "$warStaging\WEB-INF\classes" -Recurse -Force
Copy-Item -Path "$projectDir\src\main\webapp\WEB-INF\beans.xml" -Destination "$warStaging\WEB-INF\beans.xml" -Force

$warFile = "$targetDir\stayhub-service-hoteles.war"
if (Test-Path $warFile) { Remove-Item -Force $warFile }

Push-Location $warStaging
& "$jdkBin\jar.exe" -cvf "$warFile" * | Out-Null
Pop-Location

# Desplegar en WildFly
$deployDir = "$wildflyDir\standalone\deployments"
Write-Host ">> Desplegando en: $deployDir" -ForegroundColor Green
Copy-Item -Path $warFile -Destination "$deployDir\stayhub-service-hoteles.war" -Force

Write-Host ">> Despliegue completado con exito!" -ForegroundColor Green
