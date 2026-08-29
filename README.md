# 🛡️ RecordShield – Vivo In-Call Touch Protection & Survivability System

[![Device](https://img.shields.io/badge/Target_OS-Vivo_Funtouch_OS-blue.svg?style=for-the-badge&logo=android)](https://www.vivo.com/)
[![Android](https://img.shields.io/badge/Android-14%2B_%2F_16-green.svg?style=for-the-badge&logo=android)](https://developer.android.com/)
[![Release](https://img.shields.io/badge/Download_APK-v2.1.1-brightgreen.svg?style=for-the-badge&logo=github)](https://github.com/Max000110/RecordShield/releases/latest)

> ⚠️ **IMPORTANT SPECIFICITY NOTICE:**  
> **RecordShield is engineered SPECIFICALLY ONLY FOR VIVO SMARTPHONES running Funtouch OS / OriginOS (e.g., Vivo V40, V2348, Android 14+/16).**  
> It targets the proprietary Vivo In-Call UI package (`com.android.incallui`) and resource layout (`id/record_or_contacts` - `0x7f0a03f1`). It is **NOT** intended for stock Google Phone dialers or other OEM UI implementations.

---

## 📌 Problem & Objective

### 📱 The Problem on Vivo Devices
On **Vivo Funtouch OS** smartphones (such as Vivo V40), automatic call recording works reliably in the background (`/sdcard/Recordings/Record/Call/`). However, during active calls, the system In-Call UI (`com.android.incallui`) displays an on-screen **"Record"** button (`id/record_or_contacts`).

Accidental ear, cheek, or proximity touches frequently hit this button, **unintentionally stopping call recordings without the user noticing**.

### 🎯 The RecordShield Solution
- 🟢 **PRESERVE NATIVE RECORDING:** Keep Vivo automatic background call recording active (`call_record_state_global=1`).
- 🔴 **TOUCH BLOCK RECORD BUTTON:** Draw a precise non-interactive touch overlay directly over `id/record_or_contacts` (`0x7f0a03f1`).
- ⚡ **KEEP ALL OTHER CONTROLS WORKING:** Mute, Speaker, Hold, End Call, Keypad, and Bluetooth remain 100% functional.
- 🛡️ **EXTREME SURVIVABILITY:** 5-layer auto-recovery mechanism preventing OEM background termination.

---

## ⚡ Key Features

- 🛡️ **Dynamic Touch Overlay:** Calculates precise pixel bounds of `id/record_or_contacts` and places a transparent/tinted overlay using `TYPE_APPLICATION_OVERLAY`.
- 📞 **Zero Interruption:** Background call recording engine continues recording uninterrupted.
- 🔄 **Real-Time UI Re-Evaluation:** Instantly recalculates shield bounds if the call UI adjusts when toggling Speaker, Keypad, or Bluetooth.
- 🔋 **Vivo OEM Survival Assistant:** Integrated `DiagnosticsActivity` with 1-tap shortcuts for Vivo **Auto-Start**, **High Power Consumption**, and **Battery Optimization Exemption**.
- 🤖 **5-Layer Auto-Recovery Architecture:**
  1. 🥇 **Hardened Foreground Service:** `ShieldForegroundService` with `START_STICKY` & `onTaskRemoved` hooks.
  2. 🥈 **AlarmManager Watchdog:** `RTC_WAKEUP` exact alarm tick every 5 minutes.
  3. 🥉 **WorkManager Worker:** Periodic background worker every 15 minutes.
  4. 🏅 **Direct-Boot Aware Receiver:** Automatically restores protection after reboot (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`).
  5. 🎖️ **System State Heartbeat:** Reacts to power plug/unplug and screen unlock events.
- 🚨 **5-Consecutive-Failure Recovery:** Triggers FULL RECOVERY MODE after 5 consecutive background kills.

---

## 🏗️ Architecture Overview

```mermaid
flowchart TD
    InCallUI[📞 Active Vivo Call: com.android.incallui] --> AccService[🛡️ RecordShieldAccessibilityService]
    AccService --> DFS[🔍 Find Node: record_or_contacts 0x7f0a03f1]
    DFS --> Extract[📐 Extract Exact Bounds [L,T][R,B]]
    Extract --> OverlayMgr[🎨 TouchShieldManager]
    OverlayMgr --> OverlayWindow[🛑 Overlay over Record Button ONLY]

    OEMKill[❌ Funtouch OS Kill] --> L1[Layer 1: FGS START_STICKY]
    OEMKill --> L2[Layer 2: EmergencyRestartReceiver 3s Alarm]
    OEMKill --> L3[Layer 3: ShieldWatchdog WorkManager]
    OEMKill --> L4[Layer 4: SentinelAlarmWatchdog 5min Alarm]
    OEMKill --> L5[Layer 5: BootReceiver Direct Boot]

    L1 --> ThresholdCheck{Consecutive Kills >= 5?}
    ThresholdCheck -- Yes --> FullRecovery[🔥 FULL RECOVERY MODE: Flush & Re-Register Watchdogs]
    ThresholdCheck -- No --> NormalRestart[🔄 Restart Shield Service]
```

---

## 📱 Specifically Designed for Vivo (Funtouch OS)

| Specification | Vivo Specific Detail |
| :--- | :--- |
| 📱 **Target Brand** | **Vivo / iQOO** |
| ⚙️ **Operating System** | **Funtouch OS 14+ / 16 / OriginOS** |
| 📦 **In-Call Package** | `com.android.incallui` |
| 🎯 **Target Resource ID** | `com.android.incallui:id/record_or_contacts` (`0x7f0a03f1`) |
| 📂 **Recording Storage Path** | `/sdcard/Recordings/Record/Call/` |
| 🔧 **System Setting Key** | `call_record_state_global=1` |
| 🚀 **OEM Auto-Start Activity** | `com.vivo.permissionmanager.activity.BgStartUpManagerActivity` |
| 🔋 **High Power Usage Activity** | `com.iqoo.secure.ui.phoneoptimize.HighPowerConsumptionActivity` |

---

## 📥 Download & Installation

### Option 1: Direct APK Download 📦
Download the latest APK release directly from GitHub Releases:
👉 **[Download RecordShield v2.1.1 APK](https://github.com/Max000110/RecordShield/releases/latest)**

### Option 2: Build & Deploy via ADB 🛠️
```bash
git clone https://github.com/Max000110/RecordShield.git
cd RecordShield
./build_install.sh setup
```

### 🔓 Required ADB Permissions
```bash
# Grant System Alert Window (Overlay) permission
adb shell appops set com.vivorecordshield.debug SYSTEM_ALERT_WINDOW allow

# Grant Battery Optimization Exemption
adb shell cmd deviceidle whitelist +com.vivorecordshield.debug
adb shell dumpsys deviceidle whitelist +com.vivorecordshield.debug
```

### ⚙️ Required On-Device Settings (Vivo Funtouch OS)
1. **Enable Accessibility Service:**  
   `Settings` → `Accessibility` → `Installed Apps` → **RecordShield** → **ON**
2. **Enable Auto-Start:**  
   `Settings` → `Apps` → **RecordShield** → **Auto-start** → **ON**
3. **Disable Battery Restrictions:**  
   `Settings` → `Apps` → **RecordShield** → **Battery** → **No restrictions**

---

## 🧪 Automated Testing Suite

RecordShield includes a complete ADB automation script (`build_install.sh`):

```bash
# Launch RecordShield Dashboard & Telemetry
./build_install.sh start

# Stream real-time protection logcat
./build_install.sh logs

# Test single process failure recovery
./build_install.sh simulate-kill

# Test 5-consecutive-kill Full Recovery Mode
./build_install.sh simulate-5-kills

# Verify call recording files
./build_install.sh verify
```

---

## 📄 License & Disclaimer

Open-source utility for **Vivo Funtouch OS** devices.  
*Disclaimer: Not affiliated with or endorsed by Vivo Mobile Communication Co., Ltd.*
