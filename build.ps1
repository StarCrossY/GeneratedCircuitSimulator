# 编译项目：将 src 下所有 Java 源文件编译到 out 目录。
# 用法（在项目根目录执行）：powershell -ExecutionPolicy Bypass -File build.ps1
$ErrorActionPreference = "Stop"

$src = Get-ChildItem -Path "src" -Recurse -Filter "*.java"
if (-not $src) {
    Write-Host "未在 src 下找到任何 .java 文件" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

javac -encoding UTF-8 -d out $src.FullName
if ($LASTEXITCODE -eq 0) {
    Write-Host ("编译成功，共 " + $src.Count + " 个源文件") -ForegroundColor Green
} else {
    Write-Host "编译失败" -ForegroundColor Red
    exit $LASTEXITCODE
}