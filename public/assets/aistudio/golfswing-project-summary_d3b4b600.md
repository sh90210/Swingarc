# Golf Swing App — Project Summary

**Owner:** Sung (GitHub `sh90210`)
**Repo:** [github.com/sh90210/golfswing](https://github.com/sh90210/golfswing)
**Live app:** https://sh90210.github.io/golfswing/ (GitHub Pages, `main` branch)
**Period:** 15–17 November 2026

---

## 1. What the app does

A web app (PWA) for the driving range or home practice. Set the phone on a stand, tap start, and it loops hands-free:

**countdown → record the swing → replay it → countdown again**, until you tap stop.

### Core features
- **Hands-free loop.** Defaults are a 3 s countdown, 4 s recording, 2 replays at normal speed and a 1 s gap between replays. All of these can be changed in Settings.
- **Countdown beeps** so you know when recording starts.
- **Optional slow-mo replays:** off, last replay or all, at ¼×, ½× or ¾×. Off by default.
- **Video quality** (480p / 720p / 1080p) and **frame rate** (30 / 60 fps).
- **Front or back camera.** A flip button sits next to Settings, and mirroring applies only to the front camera.
- **Screen stays awake** during a session, and the session stops if you leave the app.
- **Dark, minimal design**, portrait only. Installable to the Android home screen.

### Saving and gallery (Supabase)
- **Save button** (cloud icon) appears during a replay and after you stop. The upload runs in the background, so the loop doesn't pause.
- **Sign in with GitHub** once. Only your account can see, add or delete your swings.
- **Gallery** opens from the thumbnail next to the start button. Swings are grouped by day (Today, Yesterday, weekday, date), three per row, with time and length. A storage meter shows usage against the free 1 GB.
- **In-app player:**
  - play/pause, scrubbing, 1× / ½× / ¼× speed, loop and delete (tap twice to confirm)
  - swipe to move between swings
  - tap the video to hide or show the controls, like YouTube
  - a **Trace on/off** button above Loop

### Club-head swing trace
- Worked out **automatically after every recording**: red on the backswing, green on the downswing. The line draws itself as the video plays.
- If tracing isn't finished, the loop keeps replaying until it is, so at least one replay shows the trace.
- **Trace on/off** button in the top bar during replays and in the gallery player.
- Saved swings keep their trace. Older saved swings re-trace automatically when opened after a tracer update.

---

## 2. How it's built

Plain HTML/CSS/JavaScript, with no framework and no build step.

| File | Purpose |
|---|---|
| `index.html` | Screens: camera, gallery, player, settings |
| `styles.css` | Dark minimal styling |
| `app.js` | Camera, record/replay loop, settings, saving, trace toggle |
| `tracer.js` | Club-head tracer |
| `gallery.js` | Gallery and in-app player |
| `supabase.js` | Small Supabase client (GitHub sign-in, storage, table) |
| `sw.js` / `manifest.json` / `icons/` | PWA: offline cache and home-screen install |

### Supabase setup
- **Project:** `golfswing`, Seoul region (`ap-northeast-2`), free plan.
- **Table `public.swings`:** one row per saved clip, with columns `recorded_at`, `video_path`, `thumb_path`, `duration_ms`, `size_bytes`, `mime_type`, `trace` (JSON) and `facing` (front/back camera).
- **Private storage bucket `swings`:** files at `<user_id>/<YYYY-MM-DD>/<HH-MM-SS-mmm>.webm`, plus a `.jpg` thumbnail.
- **Row-level security:** each signed-in user can only read, add and delete their own rows and files. The security check found no issues.
- **GitHub sign-in:** a GitHub OAuth app called "Golf Swing", with its callback at `https://nnuybfdyucinxffjrfup.supabase.co/auth/v1/callback`.
- **Free plan limits:** 1 GB storage, 5 GB downloads per month, and the project pauses after a week of inactivity. Your swings are kept; you un-pause it in the dashboard.

---

## 3. How the tracer evolved

| Version | Approach | Result |
|---|---|---|
| v1 | Body tracking (MediaPipe Pose) while recording; club guessed from motion around the hands | Few points, jagged lines, often wrong |
| v2 | Analysis moved to after recording, frame by frame; shaft-line search plus smoothing | Device bug: timestamps went backwards, so the body tracker rejected frames and produced "No trace". Fixed. |
| v3 | Swing found from hand position, not speed | Fixed the swing being detected as starting at the top of a slow backswing |
| v4 | **Background subtraction:** phone on a stand, so subtract the empty room, remove the golfer's silhouette, and take the far end of the thin piece reaching out from the hands as the club head. Clip analysed in one play-through at half speed (about 3× faster). | First traces that follow the real club in your videos |
| v5 (live) | Hidden stretches follow the hands' movement, not the clock; red→green switch moved to the end of the pause at the top | Fixed green starting while the club was still at the top |
| v6 (WIP branch `tracer-lag-wip`, not live) | Hidden downswing drawn as "drop behind the back first, swing out to the ball late" | Better on the front-camera swing; not yet reliable on the back-camera swing |

### Known limits of the current approach
- **Needs a still camera** on a stand; it won't work handheld.
- **The club head is often invisible on the downswing.** It's behind your back or blurred, so that part of the path is estimated, not seen.
- **Around impact** the line is pinned to where the ball sits.
- **Results can vary slightly between runs** on the same video.
- **Browsers record at most 30–60 fps**, so the downswing covers only a few frames.

---

## 4. Testing done
- Full app flow in headless Chrome with a simulated camera and a fake Supabase server: record → trace → save → gallery → play → speed → tap-to-hide → delete → camera flip.
- **Tracer tested on real footage:** your 16 Nov indoor swing and your two 17 Nov 60 fps swings (front and back camera), plus a pro reference clip with its overlay removed.
- **The body tracker can't run in this workspace.** Hand positions came from your phone's saved data and from hand-marked frames.

---

## 5. Recommended next steps for better accuracy
1. **Trained club-head detector (biggest improvement).** Fine-tune a small YOLO model on public golf datasets (e.g. Roboflow Universe "club head") plus about 200–300 labelled frames of your own swings. It can run in the browser (about 6 MB).
2. **Trained swing-timing model:** GolfDB / SwingNet (open source, non-commercial licence) detects address, top, impact and finish.
3. **Backend, optional:** a GPU service (Modal, Replicate, Hugging Face) for bigger trackers like SAM2. Roughly a few cents per swing and 10–30 s of waiting. Supabase stores the videos but can't run these models.
4. **Physical improvements:** more light (less blur) and higher frame rates. A native app could use the phone's 120–240 fps slow-mo.

---

## 6. Useful how-tos

**Download a saved video from Supabase**
1. supabase.com → sign in with GitHub → **golfswing** project
2. **Storage** → **swings** → your ID folder → date folder
3. Tap the `.webm` → **Download**. File names are recording times, e.g. `22-05-26-263.webm` = 10:05:26 PM.

**Get the latest app version on your phone**
Close the app fully (swipe it away) and reopen it.

**Filming tips for the tracer**
- Phone on a stand, filmed from behind (down the line).
- Whole body and club in the frame.
- 60 fps and good lighting.
