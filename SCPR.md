# Source Code Peer Review (SCPR)

## 1. Executive Summary & Architectural Health
The application core of Elden Earth operates as a single-page geographic exploration and territory-conquest web client backed by Firebase Firestore, MapLibre GL / Mapbox maps, and Three.js 3D rendering layers. The overall architecture is modular, utilizing IIFE singleton patterns across scripts like `Store`, `ServerAntiCheat`, `Geo`, `Grid`, and `Citadels`. 

However, architectural health is currently undermined by pervasive reliance on global variables (`Store`, `Geo`, `CONFIG`, `THREE`) without defensive `typeof` existence checks during early initialization and async boot sequences. Additionally, multiple modules contain unreachable dead code (legacy client fallback blocks following server-authoritative responses in `citadels.js`), uncleaned interval timers causing memory leaks in long-running sessions, and direct inline HTML string interpolation of user-supplied data in event handlers. Addressing these logical fragility points and unhandled network boundaries will substantially harden application stability and resilience.

---

## 2. Critical Security, Memory, and Logical Flaws

### High Severity
- **Unsanitized Inline Event Handler Interpolation**: `js/friends.js:290`, `js/friends.js:424-427`, and `js/friends.js:462` interpolate player names directly into inline `onclick` HTML attribute template strings, risking syntax breakage or injection vulnerabilities if names contain unescaped quotes.
- **Unchecked Global Dependencies (`Store` Reference Failures)**: Across multiple files (`js/anticheat.js:338`, `js/auth.js:107`, `js/character.js:46`, `js/diamonds.js:176`, `js/feed.js:239`, `js/grid.js:41`, `js/loading.js:156`, `js/main.js:192`, `js/multiplier.js:70`, `js/pet.js:52`, `js/pool.js:98`, `js/storage.js:283`), `Store` or `Store.get()` / `Store.getDb()` are accessed without confirming if `Store` is defined, risking unhandled `ReferenceError` or `TypeError` exceptions during boot races.
- **Unchecked Third-Party Constructors (`THREE.GLTFLoader`)**: `js/character.js:140`, `js/foliage.js:77`, `js/pet.js:283`, and `js/loading.js:76` instantiate `THREE.GLTFLoader()` directly without checking if the extension constructor is defined on `THREE`.

### Medium Severity
- **Unbounded Payload Consumption**: `server/access-control.js:77-80` reads incoming HTTP POST request bodies via `req.on("data")` without a maximum body size limit, exposing the microservice to memory exhaustion DoS attacks.
- **Uncleaned Interval Timers & Memory Accumulation**: `js/chat.js:287`, `js/diamonds.js:338`, `js/elden-stops.js:636`, `js/elden-stops.js:888`, `js/pool.js:145`, and `js/pet.js:338` establish repeating `setInterval` or active timeout schedules without clearing prior handles or tearing down listeners during re-initialization.
- **Unreachable Dead Code**: `js/citadels.js:154-213`, `js/citadels.js:527-586`, and `js/citadels.js:683-741` contain extensive blocks of legacy client-side fallback code located below early `return` statements following server-authoritative responses.
- **Function Shadowing**: `js/elden-stops.js:77-84` declares two functions named `formatCooldown` in the same scope, causing the second definition to shadow and disable the first.

### Low Severity
- **Exposed Internal Network Endpoints**: `index.html:12`, `js/config.js:29`, and `server/access-control.js` contain hardcoded internal Tailscale development domains (`https://vics-imac-1.tail37b4f2.ts.net`), leaking internal network topology.
- **Implicit Global Variable Pollution**: `js/storage.js:636-646` declares `hasConflict`, `conflictReason`, `bestAccount`, and `otherAccounts` without `let`, `const`, or `var`.

---

## 3. Non-Breaking Remediation Steps

- **For unverified global `Store` references (`js/anticheat.js:338`, `js/auth.js:107`, `js/character.js:46`, etc.)**:
  Wrap accesses with an explicit safety check:
  ```javascript
  if (typeof Store !== "undefined" && typeof Store.get === "function") { ... }
  ```
- **For inline event handler string interpolation (`js/friends.js:290`)**:
  Store friendship records in a runtime lookup map and pass only the record ID in the `onclick` string rather than serializing properties.
- **For unverified `THREE.GLTFLoader` instantiation (`js/character.js:140`, `js/pet.js:283`)**:
  Verify the constructor's presence before invocation:
  ```javascript
  if (typeof THREE !== "undefined" && typeof THREE.GLTFLoader === "function") {
      const loader = new THREE.GLTFLoader();
  }
  ```
- **For unvalidated request bodies in access-control (`server/access-control.js:77-80`)**:
  Accumulate chunks with a strict byte ceiling (e.g., 10 KB) and destroy the socket with `413 Payload Too Large` if exceeded.
- **For unreachable dead code blocks (`js/citadels.js:154-213`)**:
  Remove the unreachable fallback code blocks located below early `return` statements in `plantCapsule`, `relocateCitadel`, and `executeUpgrade`.
- **For shadowed `formatCooldown` declarations (`js/elden-stops.js:77-84`)**:
  Rename or consolidate the duplicate functions to eliminate shadowing.
- **For implicit globals in storage (`js/storage.js:636-646`)**:
  Prepend `let` to variable declarations (`let hasConflict = false; let conflictReason = null; ...`).

---

## 4. Performance & Resource Optimization Suggestions

- **Throttling DOM Node Creation Bursts**: `js/diamonds.js:15-22`, `js/elden-stops.js:338-349`, and `js/main.js:775` rapidly create and append unmanaged DOM elements (`setTimeout` floating text / particles) during heavy collection spams. Implement particle pooling or debounce creation frequency to mitigate layout reflow churn.
- **Caching DOM Queries & Eliminating Full Container Re-renders**: `js/feed.js:156-164` and `js/grid.js:372-384` completely wipe and repopulate DOM lists or marker collections (`innerHTML = ""` / clearing all markers) on every event. Implement incremental updates or DOM node recycling.
- **Batched Firestore Plot Queries**: `js/storage.js:518-538` performs sequential individual document `.get()` calls inside a synchronous `for` loop during conflict checks. Refactor these loops to utilize `Promise.all()` or batch lookup mechanisms.
- **Preventing Background Timer Accumulation**: Ensure all recurring `setInterval` timers (e.g., in `js/chat.js:287`, `js/elden-stops.js:636`, `js/pool.js:145`) store their handle and execute `clearInterval()` before module re-initialization.