# Update v1.8 — New Features

## ✅ What's New

### 1. 👤 User Profile
- **Name, Mobile Number, Address** fields
- Local storage (SharedPreferences)
- Accessible from Menu → "My Profile"
- Used by AI Agent for personalized responses
- Included in backup/restore

### 2. 🤖 AI Agent (Chat)
- **Local AI chatbot** — no internet required
- **Hinglish support** (Hindi + English mixed)
- **Knowledge base** includes:
  - App features & navigation
  - Aluminium calculations (Sutter, Muliya, RP)
  - Window types (ZED, DOMAL)
  - Pipe cutting & bin-packing algorithm
  - Price setup
  - Customer management
  - Backup/restore
  - Settings & formulas
- **Smart pattern matching** with keyword detection
- **Quick action chips** for common queries
- **Typing indicator** for natural conversation feel
- **Personalized greetings** using user profile name
- Accessible from Menu → "AI Agent (Chat)"

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
