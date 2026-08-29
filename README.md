# 🛡️ RecordShield – In-Call Touch Protection & Survivability System

[![Device](https://img.shields.io/badge/Target_OS-Vivo_Funtouch_OS-blue.svg?style=for-the-badge&logo=android)](https://www.vivo.com/)
[![Android](https://img.shields.io/badge/Android-14%2B_%2F_16-green.svg?style=for-the-badge&logo=android)](https://developer.android.com/)
[![Status](https://img.shields.io/badge/Status-Production_Hardened-brightgreen.svg?style=for-the-badge)](https://github.com/)

> ⚠️ **IMPORTANT SPECIFICITY NOTICE:**
> **RecordShield is specifically engineered ONLY for Vivo smartphones running Funtouch OS / OriginOS (such as Vivo V40, V2348, Android 14+/16).**  
> It targets the proprietary Vivo In-Call UI package (`com.android.incallui`) and resource layout (`id/record_or_contacts` - `0x7f0a03f1`). It is **NOT** designed for standard Google Phone dialers or other OEM UI layouts.

---

## 📌 Problem & Objective

### 📱 The Problem on Vivo Devices
On **Vivo Funtouch OS** smartphones (e.g., Vivo V40), automatic call recording functions reliably in the background (`/sdcard/Recordings/Record/Call/`). However, the system In-Call UI (`com.android.incallui`) displays an on-screen **"Record"** button (`id/record_or_contacts`). 

During phone calls, accidental ear, cheek, or proximity sensor interactions frequently touch this button, **unintentionally stopping call recordings without the user noticing**.

### 🎯 The RecordShield Solution
- 🟢 **PRESERVE** automatic background call recording completely intact (`call_record_state_global=1`).
- 🔴 **TOUCH-BLOCK / SHIELD** the specific in-call "Record" button area using dynamic `TYPE_APPLICATION_OVERLAY` bounds.
- ⚡ **KEEP FULL ACCESS** to all other vital call controls: **End Call**, **Mute**, **Speaker**, **Hold**, **Keypad**, and **Bluetooth**.
- 🛡️ **EXTREME SURVIVABILITY** on aggressive OEM battery management via 5 recovery layers & watchdog mechanisms.

---

## ⚡ Features & Capabilities

- 🛡️ **Targeted Touch Overlay Shield:** Draws a precise non-interactive transparent or tinted shield over the exact screen bounds of `id/record_or_contacts` (`0x7f0a03f1`).
- 📞 **Zero Recording Interruption:** Native Vivo call recorder continues running unimpeded in the background.
- 🔄 **Dynamic UI Node Bounds Re-Evaluation:** Instantly recalculates shield placement if the dialer UI updates due to Bluetooth, Speaker, or Keypad toggles.
- 🔋 **Vivo OEM Battery Saver Exemption Assistant:** Built-in `DiagnosticsActivity` provides 1-tap shortcuts for Vivo **Auto-Start**, **High Power Consumption**, and **Battery Optimization Exemption**.
- 🤖 **5-Layer Auto-Recovery System:**
  1. 🥇 **Hardened Foreground Service:** `ShieldForegroundService` with `START_STICKY` & `onTaskRemoved` hooks.
  2. 🥈 **AlarmManager Watchdog:** `RTC_WAKEUP` exact alarm tick every 5 minutes.
  3. 🥉 **WorkManager Worker:** Periodic background worker every 15 minutes.
  4. 🏅 **Direct-Boot Aware Receiver:** Restores protection immediately after boot (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`).
  5. 🎖️ **System State Heartbeat:** Reacts to power plug/unplug (`ACTION_POWER_CONNECTED`) and screen unlock (`USER_PRESENT`).
- 🚨 **5-Consecutive-Failure Recovery Mode:** Triggers full watchdog reset and process restoration after 5 consecutive OEM background kills.

---

## 🏗️ System Architecture

```mermaid
flowchart TD
    InCallUI[📞 Active Vivo Call: com.android.incallui] --> AccService[🛡️ RecordShieldAccessibilityService]
    AccService --> DFS[🔍 Search Node: record_or_contacts 0x7f0a03f1]
    DFS --> Extract[📐 Extract Exact Screen Bounds [L,T][R,B]]
    Extract --> OverlayMgr[🎨 TouchShieldManager]
    OverlayMgr --> OverlayWindow[🛑 TYPE_APPLICATION_OVERLAY over Record Button ONLY]

    OEMKill[❌ Funtouch OS Kill / Memory Pressure] --> L1[Layer 1: FGS START_STICKY]
    OEMKill --> L2[Layer 2: EmergencyRestartReceiver 3s Alarm]
    OEMKill --> L3[Layer 3: ShieldWatchdog WorkManager]
    OEMKill --> L4[Layer 4: SentinelAlarmWatchdog 5min RTC_WAKEUP]
    OEMKill --> L5[Layer 5: Direct-Boot BootReceiver]

    L1 --> ThresholdCheck{Consecutive Kills >= 5?}
    ThresholdCheck -- Yes --> FullRecovery[🔥 FULL RECOVERY MODE: Flush Alarms & Re-Register Watchdogs]
    ThresholdCheck -- No --> NormalRestart[🔄 Restart Shield Service]
```

---

## 📱 Specifically Designed for Vivo (Funtouch OS)

| Component | Vivo Specific Detail |
| :--- | :--- |
| **Target Brand** | **Vivo / iQOO** |
| **Target Operating System** | **Funtouch OS 14+ / 16 / OriginOS** |
| **In-Call Package** | `com.android.incallui` |
| **Resource ID** | `com.android.incallui:id/record_or_contacts` (`0x7f0a03f1`) |
| **Recording Storage** | `/sdcard/Recordings/Record/Call/` |
| **System Setting Key** | `call_record_state_global=1` |
| **OEM Auto-Start Activity** | `com.vivo.permissionmanager.activity.BgStartUpManagerActivity` |
| **High Power Usage Activity**| `com.iqoo.secure.ui.phoneoptimize.HighPowerConsumptionActivity` |

---

## 🛠️ Quick Start & Installation via ADB

### 1️⃣ Clone & Build
```bash
git clone https://github.com/YOUR_USERNAME/RecordShield.git
cd RecordShield
./build_install.sh setup
```

### 2️⃣ Grant Required Permissions via ADB
```bash
# Grant System Alert Window (Overlay) permission
adb shell appops set com.vivorecordshield.debug SYSTEM_ALERT_WINDOW allow

# Grant Battery Optimization Exemption (Whitelist from Doze)
adb shell cmd deviceidle whitelist +com.vivorecordshield.debug
adb shell dumpsys deviceidle whitelist +com.vivorecordshield.debug
```

### 3️⃣ Required On-Device Settings (Vivo Funtouch OS)
1. **Enable Accessibility Service:**  
   `Settings` → `Accessibility` → `Installed Apps` → **RecordShield** → **ON**
2. **Enable Auto-Start:**  
   `Settings` → `Apps` → **RecordShield** → **Auto-start** → **ON**
3. **Disable Battery Restrictions:**  
   `Settings` → `Apps` → **RecordShield** → **Battery** → **No restrictions**

---

## 🧪 Testing & Verification Scripts

RecordShield comes with a built-in automated ADB test runner (`build_install.sh`):

```bash
# Launch RecordShield Dashboard & Telemetry
./build_install.sh start

# Stream real-time protection logs
./build_install.sh logs

# Test single process failure recovery
./build_install.sh simulate-kill

# Test 5-consecutive-kill Full Recovery Mode
./build_install.sh simulate-5-kills

# Verify call recording file creation
./build_install.sh verify
```

---

## 📋 Empirical Verification Matrix

| Test Case | Status | Verified Result |
| :--- | :---: | :--- |
| 📲 **Vivo Device Identification** | **PASS** | `V2348` (Vivo V40, Funtouch OS 16) |
| 🔍 **InCallUI Resource Discovery** | **PASS** | ARSC match: `0x7f0a03f1` (`id/record_or_contacts`) |
| 🎙️ **Auto-Recording Baseline** | **PASS** | `/sdcard/Recordings/Record/Call/` files generated |
| 🛡️ **Touch Shield Overlay** | **PASS** | Blocks touches on Record button area |
| 📞 **Call Controls Usability** | **PASS** | End Call, Mute, Speaker, Hold 100% interactive |
| 🔋 **Background Survivability** | **PASS** | FGS `SYSTEM_ALLOW_LISTED` active in background |
| 🚨 **5-Failure Full Recovery** | **PASS** | 5 consecutive kills trigger FULL RECOVERY MODE |
| 🔄 **Device Reboot Recovery** | **PASS** | Auto-restores service via `BOOT_COMPLETED` receiver |

---

## 📄 License & Disclaimer

Developed as an open-source Android reverse-engineering & platform engineering utility for **Vivo Funtouch OS** devices.  
*Disclaimer: Not affiliated with or endorsed by Vivo Mobile Communication Co., Ltd.*
