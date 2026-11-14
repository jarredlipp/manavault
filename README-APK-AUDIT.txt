MANA VAULT / 2026-11-13: THIS IS A SOURCE INTEGRATION KIT, NOT A FIXED APK.

The uploaded debug ZIP had only app-debug.apk, not source. Inspection found the
new scanner helpers shipped in classes4.dex with ZERO method references from
the dex holding NativeScannerActivity. The bundled resolver also lacks the
new singleton fields. Thus this APK is still using the old live scan logic.

See APK-INTEGRATION-AUDIT-2026-11-13.md for concrete evidence, integration
requirements, and the release-gate command. Tools are in tools/.

No user data or original files are removed by this kit.
