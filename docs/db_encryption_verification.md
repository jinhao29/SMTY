# 数据库加密（SQLCipher）真机验证清单 · v1.0.5

> 为什么需要这份清单：SQLCipher 的核心是 **native 库（`libsqlcipher.so`）**。
> JVM 单元测试（Robolectric）跑在桌面 JVM 上，AAR 内是 Android ABI 的 ELF 文件，
> **无法加载** → `System.loadLibrary("sqlcipher")` 必抛 `UnsatisfiedLinkError`。
> 所以"真的加密了吗"这件事，只有真机能回答。
>
> JVM 侧测的是**决策逻辑**（文件头判定、失败保留、分支走向）；
> 真机侧测的是**加密实效**（换机、篡改、无密钥读取）。

---

## 0. 前置

```bash
adb devices          # 确认设备已连接（如 10AECS206R001Z5）
```

**关键前提：必须用带 release 签名的包验证"升级迁移"** ——
debug 包与 release 包签名不同，不能覆盖安装，会导致"假装是新装"而跳过真实迁移路径。

```bash
# 先装一个 v1.0.4（旧版，明文库）并录入测试数据，再用 v1.0.5 覆盖安装
```

---

## 1. 验收项

| # | 场景 | 操作 | 期望结果 | 判定 |
|---|---|---|---|---|
| 1 | **首次启动自动迁移** | 旧版（明文库）录入 3 个学员 → 升级安装 v1.0.5 → 打开 App | 学员数据完整可见；logcat 出现 `PlainDbMigrator: 数据库加密迁移完成` | ☐ |
| 2 | 迁移耗时 | 同上，记录从点图标到首屏可交互 | 一般库 < 2s；期间不 ANR、不白屏卡死 | ☐ |
| 3 | **库文件已加密** | `adb shell run-as com.shangmentiyu.sportscoach ls -l databases/` 然后 dup 出来查文件头 | `sports_coach_db` 前 16 字节**不是** `SQLite format 3` | ☐ |
| 4 | **无密钥读不出** | 把 db 文件拉到电脑，用 DB Browser / `sqlite3` 打开 | 报错或全是乱码，**看不到任何学员姓名/电话** | ☐ |
| 5 | 明文快照已留存 | 查 `filesDir/PlainMigration/<时间戳>/` | 存在迁移前的明文库（最后兜底） | ☐ |
| 6 | **手动备份 → 恢复** | 设置→数据管理→备份；改动数据；恢复 | 数据回到备份时点，无报错 | ☐ |
| 7 | 备份包内容 | 解密备份 ZIP 后查 `sports_coach_db` 文件头 | 同样是加密库（非明文 SQLite） | ☐ |
| 8 | **跨机恢复（核心）** | A 机备份 → 把备份文件传到 B 机 → B 机恢复 | 数据完整还原到 B 机；照片可看 | ☐ |
| 9 | 恢复前安全备份 | 恢复过程中 | logcat 出现"正在创建恢复前安全备份"，旧数据在 `SafetyBackup/` | ☐ |
| 10 | 口令错误不破坏数据 | 用错误口令恢复 | 明确报"口令不匹配"；**原有数据完好**（不自动重启） | ☐ |
| 11 | 急救备份可读 | 触发 `EmergencyBackup` 后启动 | logcat `DataRecovery: PRAGMA integrity_check = ok` + 学员数正确 | ☐ |
| 12 | 启动前避风港备份 | 每次启动 | `filesDir/PreUpdateBackup/<时间戳>/` 生成新快照（保留最近 3 份） | ☐ |
| 13 | 多租户隔离 | 切换到俱乐部模式再切回 | 两个库各自独立加密、各自数据正确 | ☐ |
| 14 | 卸载重装 | 卸载 App 后重装 | 应提示"数据库密钥已失效，请从备份恢复"（**不是**静默建空库） | ☐ |

---

## 2. 命令速查

```bash
PKG=com.shangmentiyu.sportscoach

# 迁移日志
adb logcat -c && adb shell am force-stop $PKG && adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
adb logcat -v time | grep -E "PlainDbMigrator|DatabaseKeyManager|EncryptedDb|BackupManager|DataRecovery|PreUpdateBackup"

# 把数据库拉出来看文件头（需要 run-as 可用，即非 release 强混淆包）
adb shell "run-as $PKG cat databases/sports_coach_db" > /tmp/enc.db
xxd -l 32 /tmp/enc.db        # 前 16 字节不应是 "SQLite format 3"

# 明文快照检查
adb shell "run-as $PKG ls -l files/PlainMigration/"
```

---

## 3. 本地已验证（JVM，无需真机）

- 文件头判定：明文=非加密 / 随机盐=已加密 / 空文件与短文件不崩
- 迁移失败时**明文库内容一字未改**（安全底线）
- 全新安装、零长度文件等分支不误触发迁移
- 迁移过程不残留 `.migrating` 临时文件

---

## 4. 已知约束（务必同步给用户）

1. **Keystore 密钥与设备绑定**：换机 / 卸载重装 / 恢复出厂后，本地加密库**无法直接打开**。
   跨设备迁移的唯一通道是**应用备份**（备份包用用户口令加密，与 Keystore 无关）。
2. **备份口令丢失 = 备份永久无法恢复**（无找回机制）。
3. 数据库加密**不保护**已被 root 且运行中的设备内存（属于操作系统层面防护边界）。
