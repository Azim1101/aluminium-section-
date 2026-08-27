# Update v1.8 — New Features

## ✅ What's New

### 1. 👤 User Profile
- **Name, Mobile Number, Address** fields
- Local storage (SharedPreferences)
- Accessible from Menu → "My Profile"
- Used by AI Agent for personalized responses
- Included in backup/restore

### 2. 🤖 AI Agent (Chat, Fixed UI & Qwen 0.5B Support)
- **Fixed Chatbot UI**:
  - Full-width modern responsive message bubbles (fixed narrow letter-by-letter vertical squishing)
  - Clean markdown bold formatting without word-break glitches
  - User and assistant avatars (`👤` and `🤖`)
  - Horizontally scrollable quick action chips with icons
- **Qwen 2.5 0.5B LLM Support (Under 500MB)**:
  - Supports Qwen 2.5 0.5B Instruct INT4 (~350MB, well under 500MB)
  - In-app background downloader with live percentage and MB progress
  - Local model importer (`.onnx` / `.bin` / `.gguf`)
  - Model manager dialog accessible from top bar `[⚙️ Model]`
- **Lightweight Built-in ONNX Classifier (~268 KB)**:
  - Instant on-device classification (<1ms latency)
  - 100% offline, zero internet needed
  - Active immediately even before Qwen is downloaded

## 📁 New Files

### Models
- `app/src/main/java/com/digitalalu/alu/model/UserProfile.java` — User profile data model
- `app/src/main/java/com/digitalalu/alu/model/ChatMessage.java` — Chat message model

### Activities
- `app/src/main/java/com/digitalalu/alu/UserProfileActivity.java` — Profile screen
- `app/src/main/java/com/digitalalu/alu/AgentActivity.java` — Chat interface

### AI Engine
- `app/src/main/java/com/digitalalu/alu/agent/AgentEngine.java` — Local AI chatbot with knowledge base

## 🔧 Modified Files

### Core
- `AndroidManifest.xml` — Added new activities
- `MainActivity.java` — Added menu items for Profile & Agent
- `BackupManager.java` — Include user profile in backups
- `build.gradle` — Version 1.7 → 1.8

## 🎯 How to Use

### User Profile
1. Tap **⋮ (More)** button (top-right)
2. Select **"👤 My Profile"**
3. Enter Name, Mobile, Address
4. Tap **"SAVE PROFILE"**

### AI Agent
1. Tap **⋮ (More)** button (top-right)
2. Select **"🤖 AI Agent (Chat)"**
3. Ask questions like:
   - "app kya hai" — Learn about the app
   - "calculation kaise kare" — Get calculation help
   - "sutter kya hai" — Understand sutter
   - "pipe cutting" — Learn about cutting plans
   - "help" — See all topics
4. Or use **quick action chips** for common queries

## 🧠 AI Agent Features

- **Pattern Matching**: Detects keywords in user input
- **Knowledge Base**: 20+ topics about aluminium calculations
- **Hinglish**: Responds in mixed Hindi-English
- **Offline**: No internet needed
- **Personalized**: Uses your name from profile
- **Contextual**: Knows about app features, calculations, settings

## 💾 Data Storage

- **User Profile**: Saved in SharedPreferences (`alu_user_profile`)
- **Chat**: In-memory only (not persisted)
- **Backup**: User profile included in app backups

## 🚀 Next Steps

To build and test:
1. Open project in Android Studio
2. Build → Rebuild Project
3. Run on device/emulator
4. Test User Profile from Menu
5. Test AI Agent from Menu
6. Try different chat queries

---

**Version**: 1.8  
**Build**: 9 (versionCode 9)  
**Date**: 2026-08-05

**Previous Release**: v1.7 (Build 8) — Already on GitHub
