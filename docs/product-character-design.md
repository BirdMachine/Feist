# Birdie Photo Maid — Product & Character Design Spec

Source: Birdie's v0.2 product and character brief, supplied September 24, 2026. This document preserves its product direction and implementation priorities; current shipping behavior is described in the root README.

## North star

A local-first Android photo-library cleaner with a highly expressive kestrel-themed maid mascot. Find exact and likely near-duplicates, group screenshots by originating app, surface review queues, and help users reclaim storage without accidentally losing important material. The mascot is a visual character for feedback, branding, humor, and customization; she is not an AI assistant or a conscious entity. Users may come for the character and stay for a safe, competent cleanup utility.

The character is fast, sharp-eyed, slightly cocky, extremely competent, and occasionally exhausted by the horrors in a gallery. Kestrel traits drive her behavior: she spots tiny details, locks on to targets, is small but fierce, and takes pride in efficiency. The utility should remain useful when character presence is off.

## Character design

Warm rust, tawny brown, cream, black feather markings, amber eyes, and small teal or sky-blue UI accents. Use feather-shaped hair, wing-like side locks, small feather ornaments, speckled or barred fabric, and compact energetic proportions. A modern functional maid uniform may include modest ruffles, bows, gloves, a utility pouch, and a duster or cleaning wand. She dresses like a maid and behaves like an optimized little raptor. Her individual name can emerge later; the product remains Birdie Photo Maid.

## Reusable states

| State | Trigger | Expression and sample copy |
| --- | --- | --- |
| Idle | Home and no task | Alert, relaxed, slightly impatient. |
| Searching | Indexing, hashing, comparing, classifying | Focused predator posture. “Target acquisition in progress.” |
| Found Something | Exact cluster or cleanup opportunity | Smug, holding matching cards. “Caught one.” |
| Huge Find | Major duplicate group or recovery | Feather flare and triumph. “Now THAT is a nest of duplicates.” |
| Reviewing | User decision | Quiet, attentive, holding folders. |
| Finished, small | Short completion | Proud. “Clean.” |
| Finished, big | Long completion | Briefly tired, then recovers after a few seconds. “Give me a second…” → “Okay. Worth it.” → idle. |
| Gallery Disaster | Exceptional clutter | Deadpan disbelief. “I need hazard pay.” Use sparingly. |
| Nothing to Clean | No findings | Smug approval. “Suspiciously tidy.” |

State logic must be independent of artwork. Begin with static PNG/WebP, crossfades, small transforms, eye blinks, feather movement, or breathing; consider Rive, Lottie, Live2D-style rigs, or sprites later. Dialogue should be data-driven by state, minimum intensity, rarity, tone, and content tier.

## Character controls and appearance

Character Presence: Off (conventional utility UI), Quiet (mostly static), Standard (reactive default), Animated (more motion), and Chaos (frequent jokes and silly effects). Presence is independent of appearance. Profiles can include Classic Maid (store-safe default), Feisty Maid (stronger raptor attitude), optional mildly suggestive Ecchi Maid, Battle Maid, Casual, Minimal, and a future Butler or masculine character with its own design. Suggestive variants have no nudity, sex acts, pornographic framing, or sexual interaction mechanic.

Asset and dialogue tiers: Everyone, Playful, Spicy. Core functionality never depends on a tier. Distribution builds may omit higher tiers entirely.

## Damaged-display accessibility

Independently configurable unusable Top, Bottom, Left, and Right zones; for example Bottom 34% and Right 8%. Preview translucent masks and let users drag boundaries. All required controls and important information must be reachable outside the masks. Never require bottom navigation, bottom sheets, stranded floating buttons, or gestures that begin in damaged regions. Future floating controls can dock top-left, top-center, top-right, left, or right for cracks, one-handed use, motor accessibility, and foldables.

## Screens and scan

Home shows mascot, last scan, image and screenshot counts, duplicate candidates, recoverable estimate, and review queue, with Scan Library, Review Findings, Browse Screenshots, and Settings. Scan progress should name phases: reading library, identifying screenshots, hashing exact duplicates, comparing visual candidates, sorting sources, preparing results. Always show technical counts alongside mascot reactions, such as `Hashing images 8,421 / 12,040`.

Duplicate clusters are thumbnail cards with count and potential savings. Label Exact (byte-identical), Visual match (high similarity), and Similar (related, not necessarily duplicates) distinctly. Support Keep best/newest/oldest/all, manual selection, and Ignore cluster later. Never auto-delete.

Screenshot source groups show counts and confidence, such as “Discord — High confidence” or “Probably Discord — 72%.” Begin with filename and MediaStore path, then consider package hints, OCR, visual recognition, and locally remembered user corrections. Unknown should stay unknown. Local corrections could later power heuristics, image embeddings, an on-device classifier, OCR keyword profiles, or user-generated app fingerprints.

## Organization and deletion safety

Start with internal categories, MediaStore albums, tags, and saved smart groups. Physical moves are opt-in; explain that gallery apps can react differently before moving files into paths like `Pictures/Screenshots/Discord/` or `Pictures/Screenshots/Elona Mobile/`.

Default deletion path: Candidate → Review → Android Trash or quarantine → optional permanent deletion. Proposed safety settings: Maximum (never permanently delete in Feist), Safe (Android Trash), Advanced (permanent deletion after confirmation). Destructive labels must be plain and explicit; mascot jokes can appear afterward. The current v0.3 build offers Trash and a separately labeled permanent delete action; it does not yet have a safety-level setting.

## Privacy, performance, visual effects

Hashing, matching, source grouping, OCR, and file management should run locally by default. Any future cloud analysis must be optional, explicitly scoped, and previewed before upload. Never silently upload the gallery. A user-initiated backup share is distinct from cloud analysis.

Use polished anime-tech styling: translucent panels, soft glass, feather highlights, warm gold, charcoal text, rounded cards, sparing sparkles. Visual Effects levels: Off, Light, Pretty (intended default), Maximum Bird. Degrade expensive blur to translucent solids.

Scan Intensity: Quick (metadata, screenshot detection, exact duplicates), Balanced (perceptual hashes and conservative similarity), Deep (OCR or local inference). Consider charging-only Deep scans and thermal throttling.

## Roadmap from the v0.2 brief

1. Real thumbnail review and screenshot-source browser.
2. Four-edge damage-zone editor and mascot state engine.
3. Character presence, visual effects, scan intensity settings.
4. Android system Trash workflow and persistent screenshot-source corrections.
5. Smart rules for old Maps/shopping screenshots, QR codes, receipts, memes, errors, games, references, and temporary images; screenshot bursts and timeline reconstruction.
6. Best-shot selection, OCR search, local semantic search, gallery health metrics, storage forecasts, alternate attendants and themes, cosmetic unlocks that never gate utility.

Possible taglines: “Your gallery has a mess. She has a duster.” “Fast photo cleanup with a little talon.” “A sharper way to clean your gallery.” “Your screenshots have gone feral.”
