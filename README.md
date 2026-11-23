# HONEST REWARD — status of this package
App icon: the supplied neon "JD" logo is installed in all mipmap-* folders (and a 512px Play Store icon).

## Demo (demo/index.html)
Single-file HTML preview (AR/EN, login, PUBG/Free Fire stores, orders, test admin mode). Open it in any browser. Local-only data, no real ads or server.

## Done (backend/)
Postgres schema + Node/Express API: register/login (bcrypt+JWT), packages, append-only ledger (trigger-enforced; balance == SUM(ledger)),
atomic order reservation with row locks (no double spend), reject => points released, admin order/package endpoints with role check, audit log,
ad callback with HMAC check, unique-ref duplicate protection, daily limit, disabled by default (settings.ads_enabled).

## Setup
1. `createdb honest && psql honest -f backend/schema.sql` (needs pgcrypto/PG13+)
2. `cd backend && npm i && DATABASE_URL=... JWT_SECRET=<random> AD_CALLBACK_SECRET=<from provider> npm start`
3. Create an admin: `UPDATE users SET role='admin' WHERE email='you@example.com';`

## NOT yet built (next steps)
Android UI/Kotlin screens (manifest + icons only), Google Sign-In, email verification & reset emails, FCM admin push, admin web dashboard,
ad-provider SDK (choose one whose policy allows this model; adapt /ads/callback to its signature scheme), Gradle config, APK build.

## Update 2 — backend + admin dashboard
Added (backend/src/extra.js): email verification and password reset via SMTP (nodemailer; env SMTP_URL, MAIL_FROM, PUBLIC_URL), orders require a verified email,
in-app notifications table, admin FCM push on new orders (env FCM_SERVICE_ACCOUNT_JSON; device token via POST /me/fcm), admin API (stats, user search, ledger view,
points adjust, notify, package add/edit/disable, settings). Admin dashboard: backend/public/admin/index.html, served at /admin-ui/ (log in with an admin user).
Existing DBs: run backend/migrations/002_auth_notifications.sql (already appended to schema.sql for fresh installs).

### Verification status (honest)
- Checked: `node --check` on server.js and extra.js, and the dashboard script parses.
- NOT run: npm install, the server, any endpoint, any DB transaction (this environment has no network and no PostgreSQL). Treat the backend as untested until you run it.
- Desktop alerts in the dashboard work only while the page is open (polling + Notification API); background web push is not implemented.
- NOT built: the Jetpack Compose Android app, Gradle files, Google Sign-In, the ad SDK, and the APK. This environment has no Android SDK/Gradle and no network, so an APK cannot be built here.

## Updates 3 to 11 (summary)
- AdMob SSV (backend/src/ssv.js, unit-tested) with callback /ads/callback; ads disabled until ads_enabled=true and ADMOB_AD_UNIT_IDS is set. ADMOB_TEST_MODE switch in android/gradle.properties.
- Android app (Kotlin + Compose): login kept on the device, guest mode, stores, orders, earn/ads, profile, support, tap sound, 4 themes, purple geometric pattern, Terms & Conditions (template, 31 clauses AR/EN).
- Backend hardening: DB pool settings for Neon, request error logging, rate limits, schema auto-applied on first start, daily ad limit default 1000.
- Admin dashboard: orders (game+quantity), user ledger viewer, points adjust, notify, packages, settings.
- Build: .github/workflows/android.yml (Gradle 8.7, Java 17). Not compiled locally; verify with the GitHub build.
- Terms are a TEMPLATE: have a lawyer review them; a public privacy-policy URL is still needed for AdMob/Google Play. Email (SMTP) and admin push (Firebase) are not configured yet.

## Update 12: "غنيمة" (Ghanima) redesign
- App: new brand (maroon / electric purple / electric blue / cyan), light + dark themes, Arabic/English switch with RTL, glass cards, gradient buttons, watercolor geometric background, gift-box logo + splash + launcher icon, original store illustrations (no game logos, no currency symbols).
- Screens: Home, Earn (big play button, daily counter, circular countdown overlay, "previous reward" message when AdMob gives no ad), Store (PUBG / Free Fire tabs), My Orders (Pending / Completed / Rejected), Account (change password only, theme, language, support, terms, logout), Terms (7 rules).
- Accounts: register with confirmation; "already registered" -> login; "not registered" -> sign up; session remembered. No email verification step.
- Notifications: one system notification per new server notification (order completed / rejected / admin message), polled while the app is open.
- Admin (server-checked, never in app code): ADMIN_EMAIL + ADMIN_PASSWORD + ADMIN_CODE are Railway environment variables. Login with the admin email returns a short "pre" token; the app shows the admin-code screen; only after the correct code a full admin token (mfa) is issued. All /admin/* endpoints require that token. The admin UI shows all orders with Execute / Reject / Message. The web dashboard (/admin-ui/) also asks for the code. The admin email cannot be registered by anyone.
- Ads: 10 points per verified ad, max 200/day (settings.daily_ad_limit), minimum interval between ads (settings.ad_interval_seconds, default 30) enforced on the server.
- Not compiled locally; verify with the GitHub build. Firebase push was removed; the HTML demo in demo/ shows the OLD design.

Update 13: interval between ads is 60 s (settings.ad_interval_seconds, enforced on the server); after each verified ad the Earn screen shows a large circular countdown until the next ad is allowed.
