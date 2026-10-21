# Gudi לאייפון

האפליקציה לאייפון (שלב 13.8, החלטות 25 ו-71). ההוראות: `CLAUDE.md` כאן. החשבון באפל והחנות: `docs/APPLE.md`.

## בנייה

בענן (בלי מק), הלוגיקה בלבד:

```
swift test --package-path app/ios/Packages/GudiCore
```

על מק (או ב-GitHub, `.github/workflows/ios.yml`):

```
python3 tools/build-ios-data.py
cd app/ios && xcodegen generate
xcodebuild -project Gudi.xcodeproj -scheme Gudi -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## מבנה

| תיקייה | מה |
|---|---|
| `project.yml` | הפרויקט, ל-XcodeGen |
| `Packages/GudiCore/Sources/GudiData` | הנתונים של האתר (מסלולים, רכבלים) |
| `Packages/GudiCore/Sources/GudiDomain` | החישובים: הטיול, ימי הסקי, הספירה לאחור, צבעי השיפוע |
| `Gudi/` | המסכים (היום רק שלד) |
| `Gudi/Resources/Data` | הנתונים, מועתקים מהאתר בבנייה (לא בריפו) |
