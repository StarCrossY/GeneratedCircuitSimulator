# 控制台演示：调用核心库完成直流工作点与瞬态分析，并打印结果。
# 用法（在项目根目录执行）：
#   powershell -ExecutionPolicy Bypass -File run_console.ps1                     # 使用示例网表
#   powershell -ExecutionPolicy Bypass -File run_console.ps1 我的网表.net          # 指定网表文件
param([string]$File = "examples/ce_amplifier.net")

java "-Dfile.encoding=UTF-8" -cp out simapp.ConsoleDemo $File