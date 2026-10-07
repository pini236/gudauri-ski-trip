# טיוטה לבדיקה: מטמון אמולטור נקי

הטיוטה מיועדת לסקירת בעל האנדרואיד וארכיטקט המערכת הקבוע. היא אינה שינוי בתהליך הפעיל ואינה קיצור זמן מוכח. הריצה האחרונה הייתה של חלק אחד; המטרה כאן היא קיצור זמן ההכנה של אותה משימה, ולא שינוי מספר החלקים.

## מה נתמך ומה נשמר

[התיעוד של פעולת האמולטור בגרסה הנעוצה בריפו](https://raw.githubusercontent.com/ReactiveCircus/android-emulator-runner/a421e43855164a8197daf9d8d40fe71c6996bb0d/README.md) מתאר יצירת תמונת מצב נקייה, שימוש באמולטור קיים ובדיקות בלי שמירת תמונת מצב חדשה.

שינוי חשוב ביחס לדוגמה הכללית: משתמשים בשתי פעולות מטמון נפרדות. שומרים את תמונת המצב **לפני** התקנת האפליקציה והרצת התסריט, ולא בסוף המשימה. אחרת קובצי הדיסק של האמולטור עלולים כבר לכלול חשבון, מיקום, הרשאות, שפה או נתונים שהשתנו בבדיקה, גם כשלא נשמרת תמונת זיכרון חדשה.

שומרים רק את האמולטור ששמו קבוע לצורך זה ואת קובץ ההגדרה שלו. לא שומרים מפתחות אימות מקומיים של כלי המכשיר, תוצאות בדיקות, קובצי אפליקציה או נתוני חשבון. אין מפתח שחזור כללי: כל אי־התאמה בגרסת החבילות או בתצורת המכונה יוצרת מטמון קר.

## קטע הגדרות מוצע

זה קטע להוספה בתוך משימת האמולטור אחרי הורדת קובצי האפליקציה והגדרת ההאצה. הוא מחליף את שלב האמולטור היחיד, לאחר סקירה ובדיקת ענף. שאר משתני הסביבה הקיימים של התסריט נשארים בתוקף. גרסת מערכת ההפעלה נעוצה כאן לשם ניסוי השוואתי, ואינה שינוי מומש.

החבילות מותקנות תחילה ממנהל הערכה הרשמי. מפתח המטמון נבנה מהגרסאות שהותקנו בפועל. הפעולה הנעוצה מתקינה שוב את חבילות הערכה בזמן הפעלתה; לפני הפעלה בודקים שהגרסאות לא השתנו מאז יצירת המפתח. אי־התאמה נכשלת במפורש במקום לטעון תמונת מצב לא תואמת.

```yaml
runs-on: ubuntu-24.04
env:
  ANDROID_API: ${{ inputs.api || '35' }}
  AVD_NAME: gudauri-qa-clean

steps:
  # Existing checkout, APK download and KVM steps come before this block.
  - name: Install emulator inputs and record their actual revisions
    id: avd-key
    env:
      RUNNER_IMAGE_OS: ${{ runner.os }}
      RUNNER_CPU_ARCH: ${{ runner.arch }}
    shell: bash
    run: |
      set -euo pipefail
      [[ "$ANDROID_API" =~ ^[0-9]+$ ]]
      sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
      # Preserve the sdkmanager exit status while accepting its official licenses.
      set +o pipefail
      yes | "$sdkmanager" --licenses > "$RUNNER_TEMP/avd-licenses.log"
      sdkmanager_status=${PIPESTATUS[1]}
      set -o pipefail
      test "$sdkmanager_status" -eq 0
      "$sdkmanager" --install emulator \
        "system-images;android-${ANDROID_API};google_apis;x86_64"
      cat > "$RUNNER_TEMP/avd-inputs.py" <<'PY'
      import hashlib, json, os, pathlib, sys

      def revision(relative):
          p = pathlib.Path(os.environ['ANDROID_HOME']) / relative / 'source.properties'
          props = dict(line.split('=', 1) for line in p.read_text().splitlines()
                       if '=' in line and not line.lstrip().startswith('#'))
          return props['Pkg.Revision'].strip()

      api = os.environ['ANDROID_API']
      identity = {
          'schema': 1,
          'runner_os': os.environ['RUNNER_IMAGE_OS'],
          'runner_arch': os.environ['RUNNER_CPU_ARCH'],
          'image_os': os.environ.get('ImageOS', ''),
          'image_version': os.environ.get('ImageVersion', ''),
          'api': api, 'target': 'google_apis', 'arch': 'x86_64',
          'profile': 'pixel_6', 'gpu': 'swiftshader_indirect',
          'animations': False,  # False means do not disable animations.
          'action': 'a421e43855164a8197daf9d8d40fe71c6996bb0d',
          'emulator_revision': revision('emulator'),
          'system_image_revision': revision(
              f'system-images/android-{api}/google_apis/x86_64'),
      }
      expected = pathlib.Path(os.environ['RUNNER_TEMP']) / 'avd-expected.json'
      if len(sys.argv) > 1 and sys.argv[1] == 'verify':
          if identity != json.loads(expected.read_text()):
              sys.exit('AVD input revisions changed; refuse this snapshot and rerun cold.')
      else:
          expected.write_text(json.dumps(identity, sort_keys=True))
          digest = hashlib.sha256(expected.read_bytes()).hexdigest()
          with open(os.environ['GITHUB_OUTPUT'], 'a') as out:
              out.write(f'key=gudauri-pristine-avd-v1-{digest}\n')
          # Only non-secret version/configuration evidence is printed.
          print(json.dumps(identity, sort_keys=True))
      PY
      python3 "$RUNNER_TEMP/avd-inputs.py"

  - name: Restore only a matching pristine AVD
    id: avd-cache
    uses: actions/cache/restore@0400d5f644dc74513175e3cd8d07132dd4860809 # v4.2.4
    with:
      path: |
        ~/.android/avd/gudauri-qa-clean.avd
        ~/.android/avd/gudauri-qa-clean.ini
      key: ${{ steps.avd-key.outputs.key }}

  - name: Generate a pristine snapshot on a cache miss
    if: steps.avd-cache.outputs.cache-hit != 'true'
    uses: reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d
    env:
      RUNNER_IMAGE_OS: ${{ runner.os }}
      RUNNER_CPU_ARCH: ${{ runner.arch }}
    with:
      api-level: ${{ env.ANDROID_API }}
      target: google_apis
      arch: x86_64
      profile: pixel_6
      avd-name: gudauri-qa-clean
      force-avd-creation: false
      disable-animations: false
      pre-emulator-launch-script: python3 "$RUNNER_TEMP/avd-inputs.py" verify
      emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
      script: echo "Generated a clean snapshot; no APK was installed."

  - name: Save the pristine snapshot before QA mutates userdata
    if: steps.avd-cache.outputs.cache-hit != 'true'
    uses: actions/cache/save@0400d5f644dc74513175e3cd8d07132dd4860809 # v4.2.4
    with:
      path: |
        ~/.android/avd/gudauri-qa-clean.avd
        ~/.android/avd/gudauri-qa-clean.ini
      key: ${{ steps.avd-key.outputs.key }}

  - name: Run QA with the matching existing snapshot
    uses: reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d
    env:
      RUNNER_IMAGE_OS: ${{ runner.os }}
      RUNNER_CPU_ARCH: ${{ runner.arch }}
    with:
      api-level: ${{ env.ANDROID_API }}
      target: google_apis
      arch: x86_64
      profile: pixel_6
      avd-name: gudauri-qa-clean
      force-avd-creation: false
      disable-animations: false
      pre-emulator-launch-script: python3 "$RUNNER_TEMP/avd-inputs.py" verify
      emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -no-snapshot-save -camera-back none
      script: bash app/android/qa/run.sh
```

הערות לסקירה: מצב קר מתחיל את האמולטור פעמיים לצורך יצירת המטמון, ולכן עלול להיות איטי יותר מהריצה הנוכחית. פעולה שמצאה מטמון אינה שומרת אותו אחרי הבדיקה. הקטע אינו משנה את הגדרת ההנפשות הקיימת. במקרה תמונת מצב פגומה או לא תואמת, הניסוי ייכשל; לפני שימוש קבוע צריך מסלול מבוקר שמוחק רק את האמולטור הייעודי המקומי ומבצע אתחול קר, ולא הופך כשל בדיקה להצלחה. אין להריץ את תסריט הבדיקה פעמיים אוטומטית רק משום שנכשל.

## ראיות לקבלת הניסוי

1. ריצה קרה אחת ושלוש ריצות חמות של אותו קומיט וחלק הבית, באותה תצורת מכונה. לרשום זמן התקנת חבילות, שחזור מטמון, אתחול, תסריט והעלאה בנפרד, ומדד חציון כולל. אין לייחס לשחזור האמולטור זמן שנחסך בבנייה אחרת.
2. להוכיח מתוך הלוג שהריצות החמות אכן טוענות תמונת מצב קיימת. לפקודת ההפעלה אין אפשרות שאוסרת טעינת תמונת מצב ואין מחיקה כפויה של האמולטור.
3. לפני התקנת האפליקציה בריצה חמה: האפליקציה אינה מותקנת, אין נתוני טיסה או קבוצה, השפה וההרשאות תואמות מצב נקי ואין חשבונות משתמש. קובצי מפתחות אימות המארח אינם במטמון.
4. שינוי רמת מערכת, ארכיטקטורה, פרופיל, גרסת תמונת מערכת או אמולטור יוצר מפתח אחר. שינוי חבילה בין יצירת מפתח להפעלה נכשל לפני טעינת המטמון.
5. כל צילומי הבית והשפות, בדיקת פתיחת השחרור, דוח הקריסה ומספר הכשלים נשארים זהים בבדיקה קרה וחמה. אין שינוי בכיסוי או בסינון שגיאות.
6. משימת בדיקה שנכשלה עדיין נכשלת; הניסיון החוזר משתמש במטמון הנקי המקורי, לא בנתוני הריצה שנכשלה.
7. לקבל את המטמון רק אם זמן הריצה החם הכולל קטן במדידה. לבדוק גם את גודל הארכיון ומכסת המטמון; אם השחזור עולה יותר מאתחול קר, לוותר על המטמון.

אחרי קבלת הניסוי אפשר לבחון בנפרד מטמון של חבילות הערכה שהותקנו, עם אותם גבולות גרסה ואמון. לא להוסיף אותו מראש: תמונת מערכת גדולה עלולה לעלות יותר לשחזור מאשר להורדה.

