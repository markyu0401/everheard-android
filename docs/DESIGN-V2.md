# Everheard — Interface direction 02

**Working name:** Everheard  
**Status:** design direction, October 2026  
**Mock:** `docs/design/everheard-v2.html` (six screens, light + dark)

---

## 1. Why the current UI reads as an indie app

The screens are functional and the information architecture is sound. The problem is not one bad screen; it is the absence of a deliberate visual system.

- **The type is inherited from the phone.** On the S26 Ultra the system font is a handwritten Latin face, so every English label renders in it. Chinese falls back to a clean sans, which makes the app look half-localized. A shipped product must own its type.
- **Every row is the same shape.** A tinted icon square, a title, a subtitle, a chevron, a 16 dp card. That is the default settings-list pattern, not a product with a point of view.
- **The cards do all the work.** Capture, highlights, recent sessions, quick actions and settings are all white rounded rectangles on a grey ground. The eye has no primary object to land on.
- **The blue is generic.** `#0A84FF` is the iOS system blue. It says "default", not "Everheard".
- **Five equal tabs flatten the hierarchy.** Today, Library, Projects, Insights and Settings are peers, even though capture is the job the product exists for.
- **Nothing is unmistakably Everheard.** No mark, no signature element, no ownable shape. The screens could belong to any transcription utility.

The redesign fixes those six things, not by adding decoration, but by taking a position.

---

## 2. Direction: a quiet instrument

Everheard is a private memory for conversations. It should feel like a well-made field notebook or a measuring instrument: calm, exact, a little editorial, and clearly yours.

**One sentence:** *A quiet archive of what was said, with one evergreen signal and one spine that runs through the day, the session and the week.*

The design has three moves:

1. **Editorial type.** A serif display face for the wordmark and screen titles; a grotesk for everything that is read at speed. The pairing gives the product a voice without turning the UI into a magazine.
2. **One signal.** A deep evergreen is the only chromatic accent in the chrome. It appears on the capture orb, the active state, the timeline nodes, the one data series and the primary action. Semantic colours (decision, commitment, risk, idea) are muted and only appear on labels and dots.
3. **One spine.** A 1 px vertical line with time nodes runs through Today, a session and a project. It is the product's signature element: memory as a sequence, not a pile of cards.

No gradients. No glass. No coloured icon squares. No card shadows. No dashboard chrome. The taskbar and cards are flat; structure comes from hairlines and the spine.

---

## 3. Research references

The direction is distilled from real product systems, not from a mood board:

- **Linear** — near-black precision, one accent, luminance-based elevation, type at 400/510/590 rather than shouting bold.
- **Vercel / Geist** — a grotesk with tight tracking, flat surfaces, hairline structure, and restraint as a form of confidence.
- **Apple HIG** — clear hierarchy, generous touch targets, one primary action, and typography that does the work instead of chrome.
- **Notion** — warm neutrals and whisper borders; structure without weight.
- **Material 3 (2026)** — a bottom navigation bar with four or five destinations, a prominent primary action, and edge-to-edge layout.
- **Anthropic / Claude** — a serif display face used sparingly for voice and warmth, paired with a neutral sans for utility.

What we deliberately reject: the generated-dashboard kit (four KPI tiles with icons in tinted circles, sparklines, gradient area charts, a coloured pill on every nav item, a hero greeting with an emoji).

---

## 4. Typography

### Families

| Role | Face | Source | Notes |
| --- | --- | --- | --- |
| Display / wordmark / screen titles | **Instrument Serif** Regular + Italic | Google Fonts, OFL | 34–40 sp, line-height 0.95–1.05, tracking −0.5 to −0.7 |
| UI / body / labels / figures | **Instrument Sans** 400 / 500 / 600 / 700 | Google Fonts, OFL | 11–20 sp, tracking 0 at body sizes |
| CJK display | **Noto Serif SC** Regular | Google Fonts, OFL | 14 MB static instance; used only when a title contains CJK. Latin titles keep Instrument Serif. Body CJK falls back to the ROM's sans. |

These faces are embedded in `app/src/main/res/font/` so the app never inherits the phone's handwriting theme. Licences are in `assets/fonts/OFL-InstrumentSans.txt`, `assets/fonts/OFL-InstrumentSerif.txt` and `assets/fonts/OFL-NotoSerifSC.txt`.

### Scale

| Token | Face | Size / line | Weight | Use |
| --- | --- | --- | --- | --- |
| `display` | Serif | 40 / 38 | 400 | Today, Library, Memory, Settings |
| `displaySmall` | Serif | 34 / 33 | 400 | Onboarding, empty states |
| `headline` | Serif | 29 / 28 | 400 | Session and project titles |
| `title` | Sans | 20 / 24 | 600 | Card titles, sheet titles |
| `subtitle` | Sans | 16 / 22 | 600 | Row titles, session titles |
| `body` | Sans | 15 / 22 | 400 | Transcript, descriptions |
| `bodySmall` | Sans | 13 / 19 | 400 | Snippets, secondary copy |
| `label` | Sans | 12 / 16 | 600 | Section labels, pills |
| `meta` | Sans | 11 / 15 | 500 | Timestamps, counts, footnotes |

Rules:

- Serif is for **titles only**. Never set a paragraph, a button, a label or a number in it.
- Numbers use `fontFeatureSettings = "tnum"` and are right-aligned in columns.
- No text below 11 sp. Section labels are sentence case, not uppercase tracked.
- A screen uses at most two weights above 600. Bold is reserved for the one number that answers the screen's question.

---

## 5. Colour

### Light — “paper & evergreen”

| Token | Value | Role |
| --- | --- | --- |
| `ground` | `#F5F6F2` | App background; a cool sage-tinted paper, not cream |
| `surface` | `#FFFFFF` | Cards, nav, sheets |
| `surfaceAlt` | `#FAFBF8` | Wells, secondary panels |
| `sunken` | `#ECEEE8` | Search field, segmented track |
| `ink` | `#101310` | Primary text |
| `inkSecondary` | `#4E554E` | Body, descriptions |
| `inkTertiary` | `#737B73` | Meta, labels (4.6:1 on paper) |
| `hairline` | `#E2E5DE` | Structural 1 px lines |
| `hairlineStrong` | `#CDD2C9` | Control borders |
| `accent` | `#1D6B4F` | The one signal: capture, active, data |
| `accentSoft` | `#E3EFE8` | Selected chip, pill, pale data bar |
| `accentInk` | `#0B3D2C` | Text on `accentSoft` |
| `live` | `#C24E3A` | Recording only; not decoration |
| `decision` | `#2F5D8A` | Decision label/dot |
| `commitment` | `#A66A1E` | Commitment label/dot |
| `risk` | `#A9453C` | Risk label/dot |
| `insight` | `#6B5B8E` | Idea label/dot |

### Dark — “true black, mint signal”

| Token | Value | Role |
| --- | --- | --- |
| `ground` | `#0A0C0A` | True black with a green cast |
| `surface` | `#121512` | Cards, nav |
| `surfaceAlt` | `#181C18` | Wells |
| `sunken` | `#070907` | Search, track |
| `ink` | `#F1F4EF` | Primary text |
| `inkSecondary` | `#A9B0A8` | Body |
| `inkTertiary` | `#7C857C` | Meta |
| `hairline` | `#232823` | Structural line |
| `hairlineStrong` | `#343B34` | Control border |
| `accent` | `#74D3A8` | Mint signal |
| `accentSoft` | `#143126` | Selected chip, pale data bar |
| `accentInk` | `#B9F3D6` | Text on `accentSoft` |
| `live` | `#F08A72` | Recording |
| `decision` | `#8FB8DD` | Decision |
| `commitment` | `#E0B36A` | Commitment |
| `risk` | `#E08B84` | Risk |
| `insight` | `#B9A8DD` | Idea |

Rules:

- Colour is on **data, status and the one primary action**, not on chrome. No tinted nav rows, no coloured icon containers.
- The accent may cover at most ~4% of a screen.
- Status colour is never the only carrier of meaning; the label says "Decision", "Open", "Listening".
- A zero is printed as `0`; a missing value is a grey dash. Never a coloured zero.

---

## 6. Layout and components

### Grid

- 4 dp base; screen gutters **20 dp**; section gap **22–28 dp**; card padding **16–18 dp**.
- One left edge and one right edge per screen. Every block shares the 20 dp gutter.
- Content sits above the taskbar; the capture orb overlaps the taskbar by 16 dp, so scroll content gets 16–20 dp bottom padding.
- Radii: cards **24 dp**, controls **14 dp**, chips/pills **999 dp**, wells **18 dp**. Three radii only.
- Surfaces: a 1 dp hairline border, no shadow. The only shadow is under the capture orb.
- Touch targets: 48 dp minimum; nav items 56 dp tall; rows 48–56 dp.

### Signature components

**The mark.** Three nested arcs opening upward with a dot at the centre. It reads as a sound wave, an ear, and a horizon at once. Used at 20 dp in the top bar, 56 dp in empty states, and as the 24 dp capture orb glyph.

**The capture orb.** A 54 dp filled circle in the taskbar, centred, overlapping the bar by 16 dp. Idle: accent fill, microphone glyph, label **Listen**. Recording: live fill, stop square, label **Stop**. It is the product's main job, always one tap away.

**The spine.** A 1 px vertical line at x = 54 dp with a 7 dp node per moment. Time on the left in tabular figures; title, one-line evidence and a type label on the right. Used on Today, session detail and project detail. The line is structural, not decorative.

**The arc.** Used in the top bar and in empty states. The Today hero is typographic, not illustrated; the large figure is the primary object.

**The well.** A `surfaceAlt` panel with a 1 dp border and 18 dp radius, used for privacy facts and key-value summaries. Not for navigation.

### Navigation

Four destinations plus one primary action:

`Today · Library · [ Listen ] · Memory · Settings`

- **Today** — the day's listening, the thread, open loops. Icon: outlined calendar.
- **Library** — all sessions, search, filters, grouped by date. Icon: outlined open book.
- **Listen** — starts/stops background capture from any tab; a separate "Live transcript" text action opens the word-by-word view. The orb is the only filled accent in the taskbar.
- **Memory** — folds Projects and the weekly review into one tab with a segmented control, defaulting to **This week**. Icon: outlined spark.
- **Settings** — Experience, Voice & people, Intelligence (provider/MCP), Capture (schedule), Privacy, Data. Icon: outlined sliders.

Detail screens (session, project, person, live, schedule, provider, MCP, voice enrolment) keep a back affordance and no taskbar.

---

## 7. Screen specifications

### 7.1 Today

**Question:** *What did I hear today, and what is still open?*

- Top bar: mark + `Everheard` wordmark (serif 21), search icon.
- Title row: `Today` (serif 40; Noto Serif SC when the title is CJK) with `10月27日 · 周二` on the right (Chinese) or `Tue, 27 Oct` (English).
- Capture hero (surface card, 24 dp):
  - Live status: dot + `Listening`/`Ready`/`Paused`.
  - Privacy marker on the right: lock glyph + `On-device` / `本机`.
  - Big figure: `2h 14m` + `heard today` (34 sp / 12.5 sp).
  - Three-cell strip: conversations / open loops / ideas, each centred.
  - Footer: `Open live transcript →`.
- `The thread` — up to 3 semantic moments from today, as a spine. Each: clock time, node colour by type, session title, one-line evidence, type + duration.
- `Still open` — up to 3 commitments from today, as a well: 17 dp checkbox ring, text, due hint.
- Privacy note: lock glyph + `Audio is deleted. Text stays encrypted on this phone.`
- Taskbar, Today active, Listen orb idle.

Empty state: keep the hero, show `Nothing heard yet` in the thread frame, and a `Start listening` text action. Do not replace the screen with an illustration.

### 7.2 Library

**Question:** *Where is that conversation?*

- Top bar: mark + wordmark; overflow.
- `Library` (serif 40) with `42 sessions`.
- Search field: 42 dp, sunken, 14 dp radius, 16 dp search glyph, placeholder `Search words, people, projects`.
- Filter chips: `All` (active), `Today`, `This week`, `Starred`. 28 dp, pale accent for active.
- Date groups: `Today · 3`, `Yesterday · 2`, etc., 11 sp/600 tertiary.
- Session row: 40 dp time column, title 14/600, one-line snippet 12/400, duration 10.5/500 on the right, amber star if marked. 1 px hairline between rows, no card.
- Empty state: 64 dp arc mark, `Nothing heard yet`, `Start listening` primary button.
- Taskbar, Library active.

### 7.3 Session detail

**Question:** *What was decided, promised or worth keeping here?*

- Top bar: back chevron, `Session` centred, star / share / delete on the right.
- Title: serif 29, two lines max; meta `Tue, 27 Oct · 09:12 · 12 min`.
- Speaker chips: `You` (accent soft), others (sunken).
- Summary card: label `Summary`, serif 21 pull quote, evidence quote + timestamp in 10.5 sp.
- `Timeline` — spine of 3–5 events; each type label coloured, evidence text, timestamp, speaker.
- `Open loops` — commitment well; `Add to calendar` text action on rows with a due hint.
- `Ideas` — inspiration rows.
- `Transcript` — section label with line count; transcript rows: speaker 10.5/600 accent/amber, timestamp 10.5, text 13/1.45. Tap a row to mark/unmark; marked rows get a pale amber wash.
- `Refresh analysis` as a text action in the summary header or overflow, not a full-width primary button.
- No taskbar; content scrolls to the bottom safe area.

### 7.4 Memory

**Question:** *What keeps coming back, and what does the week look like?*

- Top bar: mark + wordmark; overflow.
- `Memory` (serif 40) with `Last 7 days`.
- Segmented control: `Projects` / `This week`, 40 dp, sunken track, white active pill with a hairline.
- **Projects view:** rows with project name (16/600), `12 sessions · last today`, keyword line, open-loop count, and a 7-day mini bar strip. Tap opens project detail.
- **This week view:**
  - Big figure `4h 32m` + `heard this week`; delta `+38m vs last week` only when both periods have data.
  - `Daily listening` card: seven bars, one accent bar for today, pale accent for the rest; 3 faint grid lines; day labels 10.5.
  - Four-cell strip: sessions, decisions, open loops, risks.
  - `What stands out`: two observations, each with title, one-sentence body, and a citation line of timestamps. Tap a citation opens the session.
  - `3 active projects` link row switches to the Projects segment.
- Taskbar, Memory active.

### 7.5 Settings

**Question:** *Is this private, and is it set up the way I want?*

- Top bar: mark + wordmark; version pill.
- `Settings` (serif 34) + `Privacy, voice, and how Everheard works.`
- **Experience:** Appearance and Language as tap-to-expand dropdown rows. Current value on the right, chevron. Menu items show a check on the selected one.
- **Voice & people:** Voice profile (language summary or `Not enrolled`), People.
- **Intelligence:** Model provider (Connected pill or `Not configured`), MCP server (On/Off).
- **Capture:** Schedule (sleep window summary).
- **Privacy:** a well with `On this device only` header and three key-value rows: Audio deleted after transcription; Transcripts AES-256 · Keystore; Cloud backup Off.
- **Data:** `Clear all transcripts` in live red, last, with a confirmation that names the consequence.
- Footer: `Everheard 0.1.0 · Apache-2.0`.
- Taskbar, Settings active.

### 7.6 Live transcript

- Top bar: back, `Live` centred, `On-device` pill.
- Serif `Live transcript` title, subtitle `Speak naturally. Words appear as you go.`
- A large waveform card: 7 bars in accent, one line of state (`Listening`, `Finishing…`), elapsed time.
- Transcript area: speaker label + timestamp + text; latest line in ink, earlier lines secondary.
- Bottom: one primary `Stop recording` / `Start recording`; no taskbar.

### 7.7 Schedule, People, Provider, MCP, Voice

Use the same shell and components:

- Serif screen title + one-line subtitle.
- A `surface` card with 1 dp border and 24 dp radius for the main content; rows separated by hairlines.
- Fields: label above, 44 dp input with 14 dp radius and a `hairlineStrong` border; no floating labels.
- Schedule rules: a list of wells; `Block`/`Allow` as a pill; `Add rule` as a text action.
- People: rows with initials in a neutral disc, name, last talked, open loops; detail uses a spine of quotes.
- Provider/MCP: fields in a card; one primary `Save`; a `Test connection` text action; status pill.
- Voice enrolment: language dropdown, prompt card, a 30 s progress ring, `Record` / `Stop and save` primary; one language minimum.

### 7.8 Onboarding

- Full-bleed paper ground; 64 dp arc mark at the top.
- Serif 34 `Remember what was said.`
- Three feature rows without icon boxes: `Visible capture`, `Text, not audio`, `A sleep window`; title 16/600, body 13/400.
- Consent checkbox in a well; `Read the legal notes` text action.
- `Continue` primary button, disabled until consent.
- No taskbar.

---

## 8. Motion

- Press: 0.98 scale, 120 ms; no bounce.
- Capture orb: a slow 2.4 s breathing ring while recording; waveform bars animate at 8 fps. Stop when idle.
- Screen transitions: the platform default; no custom entrance animations on content.
- Dropdowns and sheets: standard Material motion.
- No parallax, no scroll-jacking, no shimmer.

## 9. Accessibility

- All text meets 4.5:1 on its surface; `inkTertiary` is the lightest grey allowed.
- Minimum 11 sp text; 48 dp touch targets.
- Icons carry `contentDescription`; the capture orb says `Listen`/`Stop`.
- Colour is never the only signal: type labels and status words are present.
- Font scaling: layouts use `sp` and wrap; no fixed-height text rows.
- `FLAG_SECURE` stays on transcript, project, insight and person screens.

## 10. Implementation notes

- Embed Instrument Sans (400/500/600/700) and Instrument Serif (regular/italic) in `res/font`. Never rely on the system font for Latin.
- Replace the Material colour scheme, typography and shapes; keep Material 3 primitives for menus, dialogs and text fields.
- Rebuild the shared components in `ui/components` so every screen inherits the system: `LargeTitle`, `GroupedCard`, `SettingsRow`, `SelectionRow`, `PrimaryButton`, `SectionHeader`, `BackButton`, `TranscriptSessionRow`.
- Add `BrandBar`, `ScreenHeader`, `CaptureHero`, `SpineTimeline`, `OpenLoops`, `SegmentedControl`, `WeeklyBars`, `MetricStrip`, `PrivacyWell`, `CaptureOrb`.
- The bottom bar becomes four destinations + orb; `MainTab` becomes `TODAY`, `LIBRARY`, `MEMORY`, `SETTINGS`.
- Add `MemoryActivity` that hosts Projects and This week; keep `ProjectDetailActivity` for the detail route.
- Keep all existing data logic, encryption, provider and MCP behaviour. This is a visual and interaction redesign, not a data rewrite.
- No new dependencies for charts: draw the seven bars with Compose layout primitives.

## 11. What not to do

- Do not put an icon in a tinted square on every row.
- Do not use a card for every section. Use hairlines and space where a card adds nothing.
- Do not use gradients, glass, glows or a coloured sidebar.
- Do not use a coloured pill on the active nav item; use ink and weight.
- Do not use five equal tabs again. Capture is not a peer of Settings.
- Do not use the system font. The phone's handwriting theme is exactly what made the old build look amateur.
- Do not invent data. Every number on the screen comes from stored sessions, memories, projects or insights.
