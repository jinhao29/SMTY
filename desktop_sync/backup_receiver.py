#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
商务体育学员管理 - 桌面端备份接收服务（v21）

用途：
    在 PC 端启动一个轻量 HTTP 服务，接收 Android App [SyncManager] 推送过来的
    备份 ZIP 文件，自动保存到指定目录，作为手机端备份的"自动同步目的地"。

设计要点：
    - 仅使用 Python 标准库（http.server / argparse），无需 pip 安装任何依赖；
    - 单文件可执行，双击或命令行启动均可；
    - 简单 token 鉴权，避免局域网内被误投递（需与 App 端 [SettingsRepository.syncToken] 一致）；
    - 自动按 "smty_backup_YYYYMMDD_HHmmss.smty_backup" 命名存储；
    - 支持跨平台（Windows / macOS / Linux），UTF-8 编码、CRLF/LF 兼容。

启动方式：
    # 默认端口 8765，保存到当前目录下的 backups/
    python backup_receiver.py

    # 指定端口和保存目录
    python backup_receiver.py --port 8765 --save-dir D:/smty_backups

    # 启用 token 鉴权（需与 App 端配置一致）
    python backup_receiver.py --token my_secret_token

    # 绑定到所有网卡（默认 0.0.0.0，局域网内手机可访问）
    python backup_receiver.py --host 0.0.0.0

协议约定（与 Android SyncManager.kt 严格对齐）：
    POST /upload
        Header:
            X-Sync-Token:  {token}            # 鉴权，两端都为空时跳过
            X-Backup-Name: {fileName}         # 备份文件名
            Content-Type:  application/octet-stream
            Content-Length: {bytes}
        Body: 备份 ZIP 原始字节流
        Response: 200 + "OK" 表示成功

    GET /health
        Response: 200 + "OK" 用于 App 端"测试连接"

兼容性：
    - Python 3.7+（标准库 http.server 已内置）
    - 不依赖 Flask / Django / FastAPI 等框架
    - 无 GUI，纯命令行服务；可在控制台窗口直接运行
"""

import argparse
import datetime
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


# === 默认配置 ===
DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 8765
DEFAULT_SAVE_DIR = "backups"
MAX_CONTENT_LENGTH = 500 * 1024 * 1024  # 500MB 上限（防异常上传撑爆磁盘）
EXPECTED_RESPONSE = "OK"


class BackupReceiverHandler(BaseHTTPRequestHandler):
    """HTTP 请求处理器：实现 /upload（接收备份）与 /health（健康检查）。"""

    # 通过类属性传递启动配置，避免每个请求都解析参数
    server_token: str = ""
    save_dir: str = DEFAULT_SAVE_DIR

    # === 路由分发 ===
    def do_GET(self):
        if self.path == "/health":
            self._send_text(200, EXPECTED_RESPONSE)
        else:
            self._send_text(404, "Not Found")

    def do_POST(self):
        if self.path == "/upload":
            self._handle_upload()
        else:
            self._send_text(404, "Not Found")

    # === 上传处理 ===
    def _handle_upload(self):
        # 1. 鉴权（两端 token 都为空时跳过校验）
        client_token = self.headers.get("X-Sync-Token", "")
        if self.server_token and client_token != self.server_token:
            self._send_text(401, "Unauthorized: token mismatch")
            self._log("鉴权失败：client_token=%r server_token=***" % client_token)
            return

        # 2. Content-Length 校验，避免恶意超大请求
        try:
            content_length = int(self.headers.get("Content-Length", 0))
        except ValueError:
            self._send_text(400, "Bad Request: invalid Content-Length")
            return
        if content_length <= 0:
            self._send_text(400, "Bad Request: empty body")
            return
        if content_length > MAX_CONTENT_LENGTH:
            self._send_text(
                413,
                "Payload Too Large: max %d bytes" % MAX_CONTENT_LENGTH,
            )
            return

        # 3. 决定保存文件名
        #    优先使用 App 端传来的 X-Backup-Name，缺失则按时间戳自动生成
        client_name = self.headers.get("X-Backup-Name", "").strip()
        if client_name:
            # 防路径穿越：仅保留文件名部分，去除任何路径分隔符
            safe_name = os.path.basename(client_name.replace("\\", "/"))
            if not safe_name:
                safe_name = self._auto_name()
        else:
            safe_name = self._auto_name()

        # 4. 确保保存目录存在
        os.makedirs(self.save_dir, exist_ok=True)
        save_path = os.path.join(self.save_dir, safe_name)

        # 5. 流式写入文件（避免一次性 read() 大文件 OOM）
        try:
            remaining = content_length
            with open(save_path, "wb") as f:
                while remaining > 0:
                    chunk_size = min(64 * 1024, remaining)
                    chunk = self.rfile.read(chunk_size)
                    if not chunk:
                        break
                    f.write(chunk)
                    remaining -= len(chunk)
            actual_size = os.path.getsize(save_path)
            self._send_text(200, EXPECTED_RESPONSE)
            self._log(
                "接收成功：%s (%d bytes) → %s"
                % (safe_name, actual_size, os.path.abspath(save_path))
            )
        except Exception as e:
            # 接收失败时清理半成品文件，避免污染保存目录
            try:
                if os.path.exists(save_path):
                    os.remove(save_path)
            except OSError:
                pass
            self._send_text(500, "Internal Server Error: %s" % str(e))
            self._log("接收失败：%s" % e, level="ERROR")

    # === 工具方法 ===
    def _auto_name(self) -> str:
        """按时间戳自动生成备份文件名。"""
        ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        return "smty_backup_%s.smty_backup" % ts

    def _send_text(self, code: int, text: str) -> None:
        """统一文本响应（UTF-8 编码，CRLF 兼容）。"""
        body = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _log(self, msg: str, level: str = "INFO") -> None:
        """简单日志：[时间] [级别] 消息。"""
        ts = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print("[%s] [%s] %s" % (ts, level, msg), flush=True)

    # 屏蔽默认的 noisy 日志，仅保留我们自定义的
    def log_message(self, format, *args):
        pass


def main():
    parser = argparse.ArgumentParser(
        description="商务体育桌面端备份接收服务（v21）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python backup_receiver.py
  python backup_receiver.py --port 8765 --save-dir D:/smty_backups
  python backup_receiver.py --host 0.0.0.0 --token my_secret_token

协议:
  POST /upload   接收备份 ZIP
  GET  /health   健康检查（用于 App 端"测试连接"）
        """,
    )
    parser.add_argument(
        "--host",
        default=DEFAULT_HOST,
        help="绑定网卡 IP（默认 0.0.0.0，局域网内手机可访问）",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=DEFAULT_PORT,
        help="监听端口（默认 8765，需与 App 端 [SettingsRepository.syncPort] 一致）",
    )
    parser.add_argument(
        "--save-dir",
        default=DEFAULT_SAVE_DIR,
        help="备份文件保存目录（默认 ./backups，不存在时自动创建）",
    )
    parser.add_argument(
        "--token",
        default="",
        help="鉴权 token（需与 App 端 [SettingsRepository.syncToken] 一致，两端都为空时跳过校验）",
    )
    args = parser.parse_args()

    # 通过类属性传递配置给 Handler
    BackupReceiverHandler.server_token = args.token
    BackupReceiverHandler.save_dir = args.save_dir

    # 创建多线程 HTTP 服务，避免大文件上传阻塞健康检查
    os.makedirs(args.save_dir, exist_ok=True)
    server = ThreadingHTTPServer((args.host, args.port), BackupReceiverHandler)

    # 启动横幅
    print("=" * 60, flush=True)
    print("商务体育桌面端备份接收服务 v21", flush=True)
    print("=" * 60, flush=True)
    print("监听地址: http://%s:%d" % (args.host, args.port), flush=True)
    print("保存目录: %s" % os.path.abspath(args.save_dir), flush=True)
    print("鉴权 token: %s" % ("已启用" if args.token else "未启用（建议局域网内启用）"), flush=True)
    print("上传上限: %d MB" % (MAX_CONTENT_LENGTH // (1024 * 1024)), flush=True)
    print("-" * 60, flush=True)
    print("App 端配置示例：", flush=True)
    print("  syncEnabled = true", flush=True)
    print("  syncHost    = <本机局域网 IP，如 192.168.1.100>", flush=True)
    print("  syncPort    = %d" % args.port, flush=True)
    print("  syncToken   = %s" % (args.token or "<留空>"), flush=True)
    print("-" * 60, flush=True)
    print("按 Ctrl+C 停止服务", flush=True)
    print("=" * 60, flush=True)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n正在停止服务...", flush=True)
        server.shutdown()
        print("已停止", flush=True)


if __name__ == "__main__":
    main()
