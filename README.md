# Pitch Flip

A 3D coin toss Android app rendered in real-time with OpenGL. Built as a fast, offline coin flipper with full customization (materials, skins, custom image uploads, custom text) and a secret "Rig Mode" that lets you control the outcome using your phone's volume keys.

---

## Rig Mode

An undetectable trick built for bets, magic tricks, or casual pranks:

- **Volume > 50%**: Lands on **HEADS**
- **Volume ≤ 50%**: Lands on **TAILS**
- **Rig Mode Off**: Completely fair 50/50 flip

Because it checks system media volume, no special permissions are needed, just adjust your volume levels before tapping the coin.

---

## How the 3D Flip Works

Instead of using pre-rendered video clips or 2D sprites, the coin is a procedural 3D model rendered with OpenGL ES:

- **Procedural 3D Mesh**: The coin faces and 60 grooved edge ridges are generated dynamically in code rather than loaded from an external 3D model file.
- **Lighting & Highlights**: Custom shaders apply ambient, diffuse, and specular lighting so the metallic surfaces and milled edges catch light naturally as the coin spins.
- **Flip Dynamics**: The flip follows natural physical stages with an initial windup tilt, flick release, high airborne arc with easing spin, and a smooth landing reveal.
- **Speed Slider**: Tune the animation speed from a slow-motion tumble to an instant flick.

---

## Customization

- **Materials & Color Palette**: Pick classic Gold or Silver, or choose from a 28-color palette (edge ridges automatically shade darker to maintain 3D rim contrast).
- **Skins**: Switch between classic engraved text, illustrated animal faces (dog, cat, duck, chicken, bear, monkey), or upload any photo from your gallery.
- **Custom Text**: Engrave your own words on Heads and Tails using the built-in Roman typeface.
- **Smart Touch**: Only tapping the coin itself triggers a flip, preventing accidental flips when opening menus or scrolling.
- **Persistent Preferences**: All your customizations (skins, custom images, colors, speed, rig toggle) automatically save between launches.

---

## Tech Stack

- **Platform**: Android 7.0+ (API 24 to 36)
- **Language**: Kotlin
- **Graphics**: OpenGL ES 2.0 via `GLSurfaceView`
- **Dependencies**: None (pure AndroidX & Material components)