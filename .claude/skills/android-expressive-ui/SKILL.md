---
name: android-expressive-ui
description: Build Android UI in the Android 17 / Material 3 Expressive design language — layered blur and translucency for depth, shape morphing, spring motion tokens, and haptics that match the motion. Use when writing or reviewing Jetpack Compose UI, when asked for a "modern Android look", frosted glass, blurred bars or sheets, expressive motion, or when pairing haptic feedback to UI events.
---

# Android 17 / Material 3 Expressive UI

The design language Android moved to across 16 and 17: **depth carried by blur rather than
shadow**, shape that morphs instead of switching, motion driven by springs instead of curves,
and haptics that belong to the same system as the animation.

This skill is the working knowledge, not a link list. Apply it whenever you write Compose UI.

---

## 1. The one idea: blur is the depth model

Shadows say "this is raised." Blur says **"there is something behind this, and it is still
there."** That difference is the whole point.

Google's stated intent for the Android 17 system UI is that blur creates "a sense of depth, so
the motion feels lightweight and you're able to stay aware of the apps you're using in the
background." Volume panel, power menu, quick settings, launcher menus, sheets — all sit on a
translucent, blurred, tinted plate rather than an opaque one.

**This is not Apple's Liquid Glass.** Liquid Glass refracts and bends what is behind it, and
the surface reads as a lens. Android's surface reads as *frosted glass*: it blurs and tints,
it does not distort. Do not add refraction, chromatic edges, or specular "liquid" highlights
when asked for the Android look — those are the other platform's vocabulary.

### The four properties of an Android 17 glass surface

| Property | Rule |
|---|---|
| **Blur** | Real blur of the content behind, not a translucent grey rectangle. |
| **Tint** | A colour wash *over* the blur, from the theme's surface role. Blur alone goes muddy over busy content; tint restores a predictable ground. |
| **Progressive edge** | The blur fades out at the edge that meets content. A hard blur boundary reads as a cut-out. |
| **Non-opaque** | Enough of the background survives to tell you what it was. If you cannot tell, use an opaque surface — you paid for the blur and got nothing. |

### Two kinds of surface — only one of them blurs

Real blur costs a full-screen copy per frame. Reach for it only where a surface **floats above
content that moves independently of it**: bars, sheets, dialogs, menu scrims, snackbars. That is
where the reader's eye actually gets caught by text sliding under text.

Everything that scrolls *with* the content — list rows, cards, chips, tiles — gets the cheap
version instead: **translucency over the app's own painted ground**, with the same tonal gradient
and specular edge. No blur, no recording, no per-frame cost, and the depth still reads because
the ground behind is genuinely visible through it.

If your app paints a flat single-colour background, this second kind buys you nothing — a
translucent surface over a flat colour is just a different flat colour. Give the app a ground
worth seeing through first.

### Anti-pattern: the ghost double-image

Fading a *single* blur radius with alpha does not produce a soft transition. At any
half-transparent pixel the sharp content and its blurred copy are both visible, and the eye
reads that as a ghosted double image, not as softness.

Frosted glass in the real world varies in **thickness**, not in opacity. Reproduce that with
**two blur passes at different radii**, each with its own alpha mask:

- a **far** pass, large radius, mask that is 0 at the content edge and 1 deep inside;
- a **near** pass, small radius, mask that peaks just inside the edge then falls away.

Where the near pass fades out, the far pass has already taken over. The transition is
continuous in *radius*, which is what the eye is actually reading.

---

## 2. Compose mechanics that actually work

### Capturing what is behind a surface

Compose cannot sample the framebuffer. Record the screen into a `GraphicsLayer` once, then
each glass surface draws the region under itself from that recording.

```kotlin
val backdrop = rememberGraphicsLayer()

Box(
    Modifier.drawWithContent {
        backdrop.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop)
    }
) { /* screen content */ }
```

Record **once per screen**, share the layer with every glass surface. A second recording is a
second full-screen offscreen pass per frame.

### The surface must not be inside what it blurs

The recording captures the content tree it wraps. A glass surface placed **inside** that tree
records itself, so on the next frame it blurs a picture that already contains its own blur — a
feedback loop that reads as a smearing, darkening haze that gets worse the longer you look.

Glass surfaces are therefore **siblings of the content, drawn after it**:

```kotlin
Box {                              // root
    Box(Modifier.drawWithContent { // 1. content, recorded
        backdrop.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop)
    }) { ScreenContent() }

    KasaTopBar(backdrop = backdrop)   // 2. glass, drawn after the record
    NavBar(backdrop = backdrop)
}
```

The practical consequence: a collapsing top bar cannot live inside the screen's `LazyColumn`.
The screen owns the scroll state and reports a progress fraction upward; the bar lives in the
scaffold. Hoisting that one float is the price of the effect.

### Two glass layers over the same region compound

A status-bar scrim and a top bar that both blur the same strip do not average — each darkens what
the other already darkened, and the overlap reads as a smudge neither produces alone. When one
surface appears over another's territory, **cross-fade them**: drive the lower one's strength
from `1 - upper` (or hide it outright). Two surfaces must never be doing the same job at the same
time.

### Drawing one blurred band

```kotlin
Box(
    Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawBehind {
            blurLayer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
            blurLayer.record { translate(-originX, -originY) { drawLayer(backdrop) } }
            drawLayer(blurLayer)
            // Mask: multiply the blurred copy by a gradient alpha.
            drawRect(
                brush = Brush.linearGradient(colorStops, start, end),
                blendMode = BlendMode.DstIn
            )
        }
)
```

Three things this depends on, all of which silently break if you skip them:

1. **`CompositingStrategy.Offscreen`** — `BlendMode.DstIn` needs a layer to punch through. Without
   it the blend reaches the real background.
2. **`positionInRoot()`, never `positionInParent()`** — the recording is in root coordinates. A
   surface that fills its parent has `positionInParent() == (0,0)`, so the offset is zero and the
   band blurs the screen's top-left corner instead of what is under it. **This failure is invisible**:
   a blurred image gives no clue which region it came from.
3. **Guard `record`** — the layer may have been released during recomposition. Wrap in `runCatching`;
   a dropped frame of blur beats a crash.

### Cost control

- Blur radius 0 must mean *no layer at all*, not a layer with radius 0.
  `if (radius > 0.5.dp) Modifier.blur(radius) else Modifier`.
- Record the backdrop only when a glass surface will actually draw. An always-on full-screen
  record is a visible slice of the frame budget at 120 Hz.
- Never read an animating `Float` in the composition phase for a blur/transform. Pass `() -> Float`
  and read it in the draw scope, or use `graphicsLayer { }`'s lambda. Reading it in composition
  recomposes the whole subtree every frame — this is the single most common cause of "the
  animation stutters".

---

## 3. Shape: morph, don't switch

Expressive shape is a *continuum*. A pressed button does not swap `RoundedCornerShape(28.dp)`
for `RoundedCornerShape(8.dp)`; it animates between them, and the animation is where the
character lives.

```kotlin
val radius by animateDpAsState(if (pressed) KasaRadius.s else KasaRadius.full, spring())
```

**Clamp animated corner radii at zero.** Springs overshoot. A spring targeting a large radius
can pass below 0 on release, and a negative corner radius throws inside `shadow()` and
`clip()` — a crash that only reproduces when the user lifts their finger a certain way.

```kotlin
val raw by animateDpAsState(target, spring())
val radius = raw.coerceAtLeast(0.dp)
```

For organic shapes (loading indicators, strength dials) use `RoundedPolygon` /
`androidx.graphics.shapes` morphing, or hand-built paths. Reuse one `Path` and one vertex
buffer across frames — allocating a `Path` per frame is thousands of objects per second at
120 Hz, and the garbage collector will eventually take a frame in the middle of the animation.

---

## 4. Motion: one vocabulary, split by distance

Expressive motion is spring-based. The mistake is picking a spring per component, which
produces a UI where every element moves at its own speed.

Split by **distance travelled**, not by component. A spring's perceived duration depends on
stiffness, not distance: the same stiffness looks like a whip over a long path and sluggish
over a short one.

| Token | Path | Used for |
|---|---|---|
| `small` | under ~40dp | press states, corner radius, switch thumbs |
| `medium` | component-sized | nav indicator, menu items, row reordering |
| `large` | screen-sized | tab change, sheets, hero elements |
| `effect` | no travel | alpha, colour — **damping 1.0, no overshoot**, or it flickers |
| `stagger` | sequential | list/menu entry, ~25 ms per step |

Enter ≈ 240 ms, exit ≈ 160 ms. The arriving surface earns the attention; the leaving one has no
right to linger.

**Direction carries meaning.** Moving right in a tab bar should bring content in from the right.
Users build a mental strip of the tab order; contradicting it costs more than the animation gains.

**Honour reduced motion.** Read `Settings.Global.ANIMATOR_DURATION_SCALE == 0` and collapse the
entire vocabulary to `snap()` behind one `CompositionLocal`. Do not make each component ask.

---

## 5. Colour, containment, hierarchy

- Use **surface container roles** (`surfaceContainerLowest` … `surfaceContainerHighest`) for
  layering, not opacity on one surface colour. Opacity stacks unpredictably over dynamic colour.
- Dynamic colour (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) is expected on Android 12+.
  **Pin semantic colours out of it**: danger stays red, success stays green. A wallpaper must not
  be able to make "weak password" look reassuring.
- Group related rows into a single container with shared outer corners and tight inner corners.
  Containment is how Expressive replaces dividers.
- Prefer tonal elevation over shadow in dark themes; shadows disappear on dark grounds.

---

## 6. Haptics belong to the motion system

A visual transition without a matched haptic feels like a video; a haptic without a matching
visual feels like a malfunction. Design them together.

### Author in affect, not in patterns

The instinct is a table: `TAP = 8 ms at amplitude 90`, `SUCCESS = two pulses`. That table breaks
twice. It breaks as it grows — the twentieth event either reuses a pattern (making two different
things feel identical) or gets a row invented for it, and none of the twenty was chosen by
looking at the others. And it breaks across hardware — fixed durations and amplitudes feel right
on the actuator of whoever wrote them and arbitrary everywhere else.

Describe **what the moment should feel like** and synthesise the waveform from it. Four axes are
enough, and each maps to one physical property:

| Axis | Question | Becomes |
|---|---|---|
| **valence** −1…1 | pleasant or not | **sharpness** — negative events are crisp and abrupt, positive ones rounded |
| **arousal** 0…1 | how much attention it demands | **intensity**, via Stevens' power law (exponent ≈ 0.7, not linear) |
| **certainty** 0…1 | finished or ongoing | **rhythm** — a completed event is one pulse; an ongoing one repeats at *uneven* intervals, because even repetition reads as "stuck" rather than "working" |
| **weight** 0…1 | trivial or irreversible | **duration and envelope** — heavy events get a faint preparatory beat first, the delay before something massive moves |

This buys three things a table cannot. New events are *placed* relative to existing ones instead
of invented. Two events can be **blended** — a scan that finishes with findings mixes confirmation
toward alarm in proportion to how many, and the in-between value is playable because the axes are
continuous. And the same description renders correctly on every rung of the capability ladder,
because the ladder converts the description, not a fixed waveform.

Keep it **deterministic**: the same affect must always feel the same. Variety destroys the only
thing haptic feedback does — the learned mapping between a sensation and an event.

Distinguish events by what actually differs to the user, not by what differs in the code:
"moved to trash" (reversible) and "deleted permanently" (not) must not share a vibration; nor
should "wrong password" and "locked out, try later" — the first invites retyping, the second must
not.

### The engine needs judgement, not just a synthesiser

Four adaptations, all of which matter more than the waveform quality:

- **Repetition damping.** The same event repeated decays exponentially toward a floor. Exempt
  alarms — the second leak is as urgent as the first.
- **A duty-cycle budget.** Cap total vibration time in a sliding window. **Drop** what exceeds it;
  never queue it. Late haptic feedback points at the wrong event, which is worse than none.
- **Context scale.** Silent mode → zero. Power save → reduced. The user's own intensity setting
  multiplies the system's; it must not be able to override it upward.
- **Learn broken paths.** If a hardware path throws once, stop trying it and drop a rung. Some
  vendor layers throw on capability queries themselves — guard each one separately and treat
  unknown as unsupported.

Since Android 16 the right primitive is the **envelope**, because it exposes the two axes people
actually perceive:

```kotlin
VibrationEffect.BasicEnvelopeBuilder()
    .setInitialSharpness(0.0f)
    .addControlPoint(/* intensity */ 1.0f, /* sharpness */ 1.0f, /* durationMs */ 60)
    .addControlPoint(0.0f, 1.0f, 90)
    .build()
```

- **intensity** ≈ how much it demands of you
- **sharpness** ≈ how crisp or soft it feels

Capability ladder, in order — each rung is a real fallback, not a nicety:

1. `vibrator.areEnvelopeEffectsSupported()` → `BasicEnvelopeBuilder`
2. `vibrator.arePrimitivesSupported(...)` → `VibrationEffect.Composition` primitives
   (`CLICK`, `TICK`, `LOW_TICK`, `THUD`, `SPIN`, `QUICK_RISE`, `SLOW_RISE`, `QUICK_FALL`)
3. `vibrator.hasAmplitudeControl()` → `createWaveform(timings, amplitudes, -1)`
4. `createOneShot` / `EFFECT_CLICK`

Rules that come from the hardware, not taste:

- **A composition containing one unsupported primitive plays nothing at all.** Check the whole set
  before using it; never assume partial support degrades gracefully.
- **Start and end waveforms at zero amplitude** so the driver can brake the actuator. Ending hot
  leaves it ringing.
- Scale differences below ~1.4× are not perceptible. Do not ship 0.5 and 0.6 as "two levels".
- Delays: <10 ms reads as one event, ~50 ms as two connected events, >100 ms as two unrelated ones.
- Route through `VibrationAttributes` with the right usage (`USAGE_TOUCH`, `USAGE_NOTIFICATION`,
  `USAGE_ALARM`) so system Do-Not-Disturb and per-category intensity settings apply.

---

## 7. Checklist before calling Compose UI "done"

- [ ] Blur bands use two radii, masked — no single-radius alpha fade.
- [ ] Backdrop recorded once per screen; no second full-screen record.
- [ ] Glass surfaces are siblings of the recorded content, never inside it.
- [ ] No two blurred layers cover the same strip at once — cross-faded instead.
- [ ] Surfaces that scroll with the content use translucency, not blur.
- [ ] Blur offset computed from `positionInRoot()`.
- [ ] Radius 0 drops the layer entirely.
- [ ] No animating float read during composition — deferred to draw/`graphicsLayer`.
- [ ] Animated corner radii clamped `>= 0.dp`.
- [ ] One motion vocabulary, split by distance; reduced motion collapses it to `snap()`.
- [ ] Semantic colours excluded from dynamic colour.
- [ ] Every meaningful transition has a matching haptic, routed with correct `VibrationAttributes`.
- [ ] Haptics described as affect and synthesised, not tabulated as fixed waveforms.
- [ ] Reversible and irreversible versions of an action feel different.
- [ ] Repetition damped, total vibration time budgeted, silent mode and power save respected.
- [ ] Effects that cost battery or frames (sensors, infinite animations) can be switched off, and
      switching them off stops the work rather than just hiding the result.
