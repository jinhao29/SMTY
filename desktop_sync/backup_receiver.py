#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
上门体育学员管理 - 桌面端双端同步服务（控制台薄壳，v23）

服务实现已下沉至桌面端工具库 student_sports_tool/data_center/sync_server.py
（与主程序内嵌同步面板共用同一实现），本文件仅保留命令行入口。

启动方式：
    # 仅接收保存（兼容 v21 行为，不合并）
    python backup_receiver.py

    # 双向同步模式（推荐）：收到备份自动合并 + 提供学员 Excel 拉取
    python backup_receiver.py --archive-dir "G:/smty_archive"

USB 连接：
    PC 执行 `adb reverse tcp:8765 tcp:8765` 后，手机端 syncHost 填 127.0.0.1
    即可经 USB 直连本服务（无需局域网）。
"""
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))


def _bootstrap_tool_root() -> str:
    """探测 student_sports_tool 工具库目录并加入 sys.path。"""
    candidates = [
        os.path.normpath(os.path.join(_HERE, '..', '..', 'student_sports_tool')),
        os.path.normpath(os.path.join(_HERE, '..', 'student_sports_tool')),
    ]
    for c in candidates:
        if os.path.isdir(c):
            if c not in sys.path:
                sys.path.insert(0, c)
            return c
    return ''


if __name__ == '__main__':
    root = _bootstrap_tool_root()
    if root:
        from data_center.sync_server import ensure_tool_paths, main
        ensure_tool_paths(root)
    else:
        # 探测失败：给出明确提示（v21 的独立实现已移除，无法降级运行）
        print('错误：未找到 student_sports_tool 工具库目录。', file=sys.stderr)
        print('请将本脚本放在 student_sports_tool 的同级仓库目录下运行，', file=sys.stderr)
        print('或改用桌面端「数据中心 → 双端同步」内嵌服务。', file=sys.stderr)
        sys.exit(1)
    main()
