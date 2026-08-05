# Permanent APK Signing Setup

Android only installs a new APK over an existing app when all three values match:

1. application ID (`com.digitalalu.alu`)
2. signing certificate / keystore
3. a higher `versionCode`

This project now uses a permanent release keystore instead of the machine-specific Android debug key. **Do not commit the keystore or its passwords.** They are intentionally ignored by Git.

## Local release build

Place the permanent key at:

```text
signing/alu-window-release.jks
```

Create an ignored `signing.properties` file in the repository root:

```properties
ALU_KEYSTORE_FILE=signing/alu-window-release.jks
ALU_KEYSTORE_PASSWORD=your-store-password
ALU_KEY_ALIAS=your-key-alias
ALU_KEY_PASSWORD=your-key-password
```

Then run:

```bash
gradle :app:assembleRelease
```

## GitHub Actions secrets

In GitHub, open:

**Repository → Settings → Secrets and variables → Actions → New repository secret**

Add these four secrets exactly as named:

| Secret | Value |
|---|---|
| `ALU_KEYSTORE_BASE64` | One-line Base64 of `alu-window-release.jks` |
| `ALU_KEYSTORE_PASSWORD` | Keystore/store password |
| `ALU_KEY_ALIAS` | Alias used while generating the key |
| `ALU_KEY_PASSWORD` | Key password |

Create the first secret locally with:

```bash
base64 -w 0 signing/alu-window-release.jks
```

The GitHub workflow restores the same key on every runner before building the release APK. Future versions will therefore update directly without uninstalling the app, as long as the same secrets and application ID are kept.

## Important: first permanent-key installation

If the currently installed app was signed with another debug/release key, Android cannot accept an update signed by this new permanent key. Uninstall the old app **one final time**, install the first permanent-key release, and keep the keystore backup safe. Every later version signed with this same key will install as an update.
