# App Audit: Guest-Recorded Shared Memory Album

**Concept:** An app where wedding/event guests upload photos & videos to a shared album. Host pays a flat fee ($10), guests contribute without needing accounts.

**Inspiration:** $20k/mo, 7.5M views. Targets $30k wedding spenders.

---

## 1. Why This Works (Markets)

**The thesis is correct:** People who spend $30k on a wedding will not blink at $10 for a shared album app. The hosting/family-reunion/graduation market is even bigger.

**The unit math works:**
- $10 per event × 2,000 events/mo = $20k/mo
- At 7.5M views, that's ~3,750 uploads per event (assuming 2,000 events) — high engagement

## 2. The Product Gap

Most existing solutions suck for different reasons:
- Google Photos — requires accounts, sharing is confusing, guests get lost
- Dropbox — clunky, no curation, no timeline view
- Shared albums in iCloud — Apple-only, guests need devices
- Wedding-specific apps — expensive, complicated, one-time use

**The insight:** This is a **guest experience** problem, not a storage problem.

## 3. Build vs Buy Decision

**Don't build from scratch.** The heavy lifting (upload, storage, sharing) is solved. What matters:
- Guest UX — scan a QR code → upload in 2 taps, no account
- Auto-curation — timeline view by time, face grouping, location
- Host experience — share QR/links, moderate content, download all as ZIP

## 4. Target Verticals (Ranked by Willingness to Pay)

| Vertical | Event Cost | Will Pay $10 | Volume |
|---|---|---|---|
| Wedding | $30k+ | ✅ Yes | High |
| Bar/Bat Mitzvah | $15k+ | ✅ Yes | Medium |
| Corporate Event | $50k+ | ✅ Yes | High |
| Family Reunion | $5k+ | ✅ Depends | High |
| Birthday (adult) | $5k+ | 🟡 Maybe | Very High |
| School Event | $3k+ | ❌ No | Low |

## 5. Critical Features (MVP)

**Guest side (no account):**
- QR code scan → web upload (no app install)
- Camera: photo + video
- Max 30 seconds per clip (keeps it snackable)
- Optional name + relation tags

**Host side:**
- Generate QR + link
- Moderation queue (approve/reject)
- Auto-timeline view
- Download all as ZIP
- Password/expiration on the album

**Nice-to-have:**
- Face detection → group by person
- Location tagging → map view
- AI highlights reel
- Print book integration (Shutterfly, etc.)

## 6. Monetization

- **Free tier:** 3 events, 100 uploads each, 7-day expiry
- **Pro ($10/event):** Unlimited uploads, 1 year storage, full res download
- **Premium ($50):** 5 years storage, print book discount, AI highlight reel

## 7. Risks

- **Storage costs:** 1,000 guests × 2 min video each = expensive. Need aggressive compression.
- **Guest adoption:** If the QR code isn't visible and pushed by the host, nobody uses it.
- **Content moderation:** Grandma's blurry photos + drunk uncle's videos need curation.
- **Competition:** Google Photos shared albums are free and improving.

## 8. Key Tactic from the OG App

The $20k/mo app likely succeeded because:
1. They targeted **wedding planners**, not brides
2. The QR code was printed on table cards (guest can't miss it)
3. The host gets the album BEFORE guests leave (immediate gratification)
4. Upsell: timeline book at checkout ($50+)

## 9. App Audit Verdict

**Worth building?** Yes, as a **thin wrapper** over existing storage (AWS S3 + CloudFront + Lambda for thumbnails). The moat is the guest UX, not the tech.

**Cost to MVP:** ~$2-3k (2 weeks, one developer, one designer)
**TAM:** 2.5M weddings/year in US alone × $10 = $25M/yr addressable

**Don't build for broke people. Build for luxury event hosts.**
