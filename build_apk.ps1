$ErrorActionPreference = "Stop"

# Set up JDK
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "Java version:"
java -version
javac -version

# Set up Android SDK
$sdkRoot = "$env:USERPROFILE\Android\Sdk"
$env:ANDROID_HOME = $sdkRoot
$cmdlineToolsDest = "$sdkRoot\cmdline-tools"

if (-not (Test-Path "$cmdlineToolsDest\latest\bin\sdkmanager.bat")) {
    Write-Host "Downloading Android Command Line Tools..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $zipPath = "$env:TEMP\cmdline-tools.zip"
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $zipPath -UseBasicParsing
    
    Write-Host "Extracting Android Command Line Tools..."
    Expand-Archive -Path $zipPath -DestinationPath $cmdlineToolsDest -Force
    
    # Android SDK requires the tools to be in a folder named 'latest'
    Rename-Item -Path "$cmdlineToolsDest\cmdline-tools" -NewName "latest"
}

$sdkManager = "$cmdlineToolsDest\latest\bin\sdkmanager.bat"

Write-Host "Accepting licenses and installing SDK components..."
# Yes to all licenses
$yes = "y`n" * 50
$yes | & $sdkManager --licenses | Out-Null

& $sdkManager "platform-tools" "platforms;android-35" "build-tools;34.0.0"

Write-Host "SDK Setup Complete. Building APK..."
Set-Location "c:\Users\SANTHOSH\OneDrive\Documents\Projs\medibridge"
.\gradlew.bat assembleDebug

Write-Host "Build finished!"
