# ChronoCard

A mentalism prediction tool built as an Android app. The performer picks a
freely-chosen card via a disguised UI, and a matching photo (uploaded ahead
of time) is inserted into the phone's gallery with a backdated timestamp —
as if it had been sitting there all along.

## How it works

**Setup screen**
1. Upload up to 52 photos, one per card, mapped by rank/suit.
2. Choose a backdate: 3h / 10h / 24h / 3 days ago, or a custom date-time.
   The three preset offsets get a small random minute/second jitter each run
   so repeat performances don't land on identical timestamps.
3. Upload a background photo used for the fake lock screen.
4. Choose input mode: **Passcode** or **Tap (AOD)**.
5. Tap **PERFORM**.

**Passcode mode** — a fake 4-digit passcode screen. Digits 1–2 select rank
(01=Ace … 10, 11=Jack, 12=Queen, 13=King), digit 3 selects suit
(1=Spade, 2=Heart, 3=Club, 4=Diamond), digit 4 is a free decoy digit. Any
code that doesn't map to a valid card triggers a shake + vibrate, exactly
like a real wrong-passcode entry, and clears back to empty. A valid code
inserts the backdated photo immediately and closes the app straight to the
home screen — a real passcode unlock never detours through a second lock
screen, so this mode skips it entirely.

**Tap (AOD) mode** — a fake always-on-display: clock, date, fingerprint
icon, and an animated "now playing" bar (Ed Sheeran – Shape of You) whose
timer loops 3:01 → 3:13 once per second. Tapping the screen at a given
instant records two things at once: the timer value in view (→ rank) and
which screen quadrant was tapped (top-left=Spade, top-right=Heart,
bottom-left=Club, bottom-right=Diamond).

This is the only mode that transitions into the fake **lock screen** —
mirroring the real AOD → lock screen → fingerprint flow: background image,
lock icon, time/date rising into place, camera/phone icons fading in next
to the music bar, and the music timer showing a confirmation value derived
from the captured card (see below) so you can verify the tap landed right.
Holding the fingerprint icon for ~0.5s plays a quick unlock animation,
silently inserts the matching photo into the gallery at the chosen
backdated time, and closes the app. If the lock screen sits untouched for
5 seconds, it auto-reverts back to AOD for another attempt.

If a card has no uploaded photo, the Ace of Spades photo is used as a
fallback — make sure that slot is filled.

## Adding your icon

The launcher icon points at `app/src/main/res/drawable/icon.png`, which
isn't included in this project — drop your `icon.png` in at that exact path
(create the `drawable` folder if it's not already there) before building.
A square image around 512x512 works well; the adaptive icon system will
crop/mask it to whatever shape the launcher uses.

## Building the APK (no local Android Studio needed)

This repo builds itself via GitHub Actions, the same cloud pipeline used for
TimeShift:

1. Push this project to a new GitHub repo.
2. GitHub Actions runs automatically on push to `main` (see
   `.github/workflows/build.yml`), or trigger it manually from the Actions
   tab ("Run workflow").
3. Once the run finishes, open it and download the `ChronoCard-debug`
   artifact — that zip contains `app-debug.apk`.
4. Sideload the APK onto the performance phone (enable "Install unknown
   apps" for whichever app you use to open it).

## Notes / things worth tightening next

- The app is now openly branded as "52 Click" (launcher label, and a header
  on the setup screen crediting ♠️Rainson Potshangbam♠️) rather than
  disguised — the passcode/AOD/lockscreen screens are still built to look
  like stock Samsung UI during a performance, but the app itself in the
  drawer no longer hides behind a generic name.
- `MediaStore` insertion covers Android 8–14; on 10+ it uses scoped storage
  (`RELATIVE_PATH` + `IS_PENDING`) correctly.
- The passcode keypad and AOD clock are built to look Samsung-ish (thin
  clock font, dimmed white-on-black AOD, rising lock screen text) but you
  may want to swap in the exact One UI font/spacing from a reference
  screenshot for a perfect match.
- Runtime permission request for gallery/photo access isn't wired into the
  setup screen yet — Android will prompt on first `GetContent()` picker
  call, but add an explicit `READ_MEDIA_IMAGES` request up front if you
  want it handled proactively.
