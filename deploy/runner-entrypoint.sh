#!/bin/sh
set -eu
# 仅清理专用运行目录中的遗留 Unix Socket，不删除同路径的普通文件
socket=/run/mocklab/runner.sock
if [ -S "$socket" ]; then rm -- "$socket"; fi
# Supervisor 通过共享 Socket 接收后端任务，runner 不监听 HTTP 端口
exec java -Xms32m -Xmx128m -cp '/app/runner.jar:/app/lib/*' dev.mocklab.runner.Supervisor unix:/run/mocklab/runner.sock
