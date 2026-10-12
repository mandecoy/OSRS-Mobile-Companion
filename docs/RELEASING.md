# Publishing a GitHub Release

The repository is prepared for both normal debug builds and signed public release APKs.

## 1. Create a permanent Android signing key

Do this once and keep the keystore backed up permanently. Future versions must be signed with the same key for Android to install them as updates.

On a computer with JDK 17 installed:

```bash
keytool -genkeypair -v -keystore osrs-companion-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias osrscompanion
```

Choose and securely save the keystore password and key password.

**Do not commit the `.jks` file or passwords to GitHub.**

## 2. Convert the keystore to Base64

### Windows PowerShell

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("osrs-companion-release.jks")) | Set-Clipboard
```

This copies the Base64 value to your clipboard.

### macOS / Linux

```bash
base64 < osrs-companion-release.jks
```

Copy the full output.

## 3. Add GitHub repository secrets

In GitHub open:

**Repository → Settings → Secrets and variables → Actions → New repository secret**

Create these four secrets:

- `ANDROID_KEYSTORE_BASE64` — the Base64 keystore contents
- `ANDROID_KEYSTORE_PASSWORD` — the keystore password
- `ANDROID_KEY_ALIAS` — normally `osrscompanion`
- `ANDROID_KEY_PASSWORD` — the key password

## 4. Build the signed public APK

Open:

**Actions → Android Release APK → Run workflow**

The workflow validates the signing secrets, builds the release APK, and uploads an artifact named like:

```text
OSRS-Companion-Tool-0.4.0
```

Download that artifact and extract the APK.

## 5. Publish the GitHub Release

On the repository page:

1. Open **Releases**.
2. Click **Draft a new release**.
3. Create the tag `v0.4.0`.
4. Set the title to **OSRS Companion Tool v0.4.0**.
5. Paste the 0.4.0 notes from [CHANGELOG.md](../CHANGELOG.md).
6. Attach the signed `OSRS-Companion-Tool-0.4.0.apk`.
7. Check **Set as the latest release**.
8. Click **Publish release**.

## Important

Never delete or lose the release keystore. If the signing key is lost, future builds signed with a different key cannot update installations of the old signed app normally.
