# Police Report (Max) — rebuilt from zero

## Latest fixes
- **Fixed a build-breaking bug**: the per-municipality cap (see below) had been wired into the primary fetch strategy, but referenced an undefined `take` variable, and the fallback strategy required a `take` parameter that no caller supplied. Neither would have compiled. Fixed both, and made the fallback strategy apply the same per-municipality cap for consistency.
- **Every municipality now gets its own fair share, guaranteed**: each selected municipality gets its own independent bucket, capped at `PER_MUNICIPALITY_LIMIT` (10) reports. A busy municipality can no longer crowd out a quiet one - the search keeps paging (up to the page budget) until *every* requested municipality has hit its own cap, not just the combined total. Since this is a live search each time (not a local store), "oldest gets replaced by newest" happens naturally: whichever 10 are most recent for that municipality *right now* are what's shown - no manual eviction logic needed. If a municipality fills its 10 in a single busy day, that's fine; if another takes 5 quiet days to get there, that's fine too.
- **Removed a hidden second bottleneck**: even with the per-municipality cap fixed at the fetch layer, both the dashboard and Max's full report screen were still applying their own flat "top 20 overall" cut afterward - which could re-introduce the exact crowding-out problem the per-municipality cap was meant to solve (and could also make the dashboard's "new incident" shield icon miss a genuinely new report in a quiet municipality if a busy one pushed it out of that top-20). Removed that extra cut; the dashboard's compact card still only *displays* 5 at a time (it's a preview widget), but the underlying data and the new-incident detection are now fair across all selected municipalities.
- Removed the now-unused `limit` parameter from the public `fetch()` API, since capping is fixed per-municipality now rather than being caller-specified.

## Earlier fixes
- Max's report cards are back to a short headline (category + street/area, e.g. "Fire · Storgata") with only the English translation as the body text - no more full Norwegian paragraph shown in the card. Tapping still opens the real Politiloggen source page (Norwegian-only, since that's the only language the site has).
- **Reports now show their full update history, not just one message**: a single incident on Politiloggen is often a thread of several updates over time (initial report, then "fire extinguished", "road reopened", etc.) - the real page shows all of them together. This app used to treat every update as its own separate card. Incidents now carry a `threadId` and are grouped by it, so each report is one card containing every update in chronological order, each fully translated, with an "N updates" badge when there's more than one. The municipality header's count now reflects distinct reports (threads), not raw message count.
- **Removed municipalities kept reappearing**: the disk cache (used as a fallback whenever a live fetch fails) returned its entire saved list unfiltered, regardless of what's currently selected in Settings. If a municipality was cached before being removed, and any later refresh fell back to that cache (which happens on any network hiccup, not just total outages), its old incidents kept resurfacing indefinitely. Fixed: the cache is now re-filtered against the *current* municipality/category selection before being served, every time.
- **Municipality sections are collapsible**: tapping a "<Municipality> Police Reports" header collapses/expands that municipality's list (with an incident count and chevron indicator), so multiple municipalities don't turn the screen into one long wall of text. Sections start expanded.

## Older fixes
- **Source link was broken**: it pointed at `politiet.no/en/politiloggen/...`, a URL that never existed - Politiloggen has **no English version at all** (confirmed via an official App Store review listing "missing: English translation" as a complaint). Fixed to the real, confirmed working pattern: `https://www.politiet.no/politiloggen/hendelse/{threadId}`.
- **Norwegian original was being discarded**: translation used to overwrite the only copy of the source text. `Incident` now keeps both `text` (Norwegian original) and `englishText` (translation) - the app shows the Norwegian original first, labeled, with the English translation underneath it, since the source itself has no English to fall back on.
- **Quiet municipalities' older-but-recent reports were invisible**: the fetcher only ever requested a single page (`Skip=0`) of the national feed. A municipality with no incidents in the very latest ~100 messages nationwide would show nothing, even with incidents from a few days back. Now pages up to 1,500 messages deep (stopping early once enough matches are found), so "a few days old" reports for the municipality you picked actually surface.
- Reordered the two fetch strategies so `/messages` (confirmed working against a real device) is tried first; `/messagethreads` is a fallback only, and had its own unverified/unconfirmed query parameters removed.
- Newest-first sorting was already correct - the "missing reports" issue above was a data-availability problem, not a sort-order one.

This feature was rebuilt completely after several earlier attempts kept
returning "couldn't reach the police report service" even when each attempt
had been individually "confirmed" against either a decompiled version of the
official Politiloggen app or a real device. The most likely explanation:
something specific to a given device/network path (carrier filtering, a DNS
blocker, etc.) was interfering with a single hardcoded request shape - not
that the public API is closed to outside callers (a separate, independent
open-source tool calling the same host works fine).

Rather than keep betting everything on getting one exact undocumented request
right, the new design assumes any single request can fail on any given
device/network, and is built to stay useful anyway.

## Architecture

**`PoliceDistricts.kt`** - the Settings picker (police district → municipality)
is now **fully offline**, a static, hardcoded reference of Norway's 12 police
districts and their municipalities. It has zero network dependency. This was
a deliberate fix: in every earlier version, the picker itself depended on a
live API call, so a single failed request broke the picker too, even though
this geography barely ever changes. Now the picker always works, regardless
of what the actual incident feed is doing.

**`PoliceReportFetcher.kt`** - fetches the incident feed itself:
1. Tries the `/messagethreads` request shape first (Skip/Take/SortByEnum/
   TimeSpanType/category, nested `messages` per thread).
2. If that fails for any reason, tries a second, independent `/messages`
   shape (Take/Skip, flat list) as a fallback strategy.
3. Whichever one returns real, parseable data wins. Both failing outright
   just means "try again later" - it doesn't have to mean "no report."
4. **The last successful fetch is cached to disk** (`police_report_cache.json`
   in the app's private files dir). If a refresh fails, the cached report is
   served instead, so a temporary network hiccup never means the report
   disappears - only a genuinely first-ever failure with no prior successful
   fetch surfaces a hard error.
5. That hard-error message is diagnostic-rich on purpose: which strategies
   were tried and what each one actually returned (HTTP status, byte count),
   so a real failure is readable from the error text alone.
6. Every incident's text is translated to English once (cached after that),
   and its real timestamp is preserved so the UI can show an actual date and
   time, not a vague "a few hours ago."
7. Tapping an incident opens its Politiloggen source page (`politiet.no/en/...`
   the English-language version of the site).

## Settings

Exactly two fields, matching the official app's own picker:
1. **Police district** dropdown (Agder, Finnmark, Innlandet, Møre og Romsdal,
   Nordland, Oslo, Sør-Vest, Sør-Øst, Troms, Trøndelag, Vest, Øst).
2. **Municipality/city** dropdown, scoped to whichever district is selected.

No freeform "type a municipality directly" option - removed on request, since
the two-step picker already covers every real municipality.

## Dashboard

A spinning shield icon (🛡️, matching Max's own mascot animation style) appears
next to the weather-alert icon whenever there's a fresh, unseen police report.
Tapping it opens Max's full report and marks the current incidents as seen.

## Background refresh

A WorkManager job checks for new incidents every hour when police alerts are
enabled, independent of whether the app is open, and posts a notification if
anything new shows up.

## If it still fails

Because every failure mode now surfaces its actual cause in the error text
(rather than one generic message), the most useful thing to do if this ever
errors again is to report that exact message back - it will say precisely
which request strategies were attempted and what happened with each one,
which is enough to diagnose without another guessing round.
