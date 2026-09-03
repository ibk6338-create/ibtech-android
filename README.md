# IB-TECH ePINs Android App — v1.2.0

Production-oriented Android client for the IB-TECH ePINs reseller service.

## Included
- Native Kotlin + Jetpack Compose UI
- Login and reseller signup
- Forgot-password request flow
- Wallet balance
- Paystack hosted checkout initiated by the IB-TECH backend
- Server-side payment verification with a pending-payment reference
- MTN, GLO, Airtel and 9mobile ePIN purchasing
- ₦100, ₦200, ₦500, ₦1,000 and ₦1,500 denominations
- Quantity up to 100 per purchase, enforced again by the server
- PIN masking and one-tap PIN copy
- Transaction history
- Dedicated Paystack bank-account creation/status flow
- HTTPS-only API communication
- App-private session storage and Android backup exclusion for the session file
- IB-TECH branding

## Backend
The app currently targets the stable backend:
`https://ibtech-epins.onrender.com`

The API endpoint is compiled through `BuildConfig.IBTECH_API_BASE_URL`, so it can be switched to a custom production API hostname later without changing the app architecture. **Do not point it at `https://ibtech.com` unless that domain is actually serving the Node API routes.** The current FreeHosting setup is for the public website; the Node backend should remain on Render or be moved to a Node-capable host. A recommended final arrangement is `https://ibtech.com` for the website and `https://api.ibtech.com` for the API.

The Paystack secret key is never included in this Android project. Payments are initialized and verified by the backend.

## Build
Open the project folder in current Android Studio, allow Gradle sync, then use:
**Build → Build APK(s)** for a test APK or **Build → Generate Signed App Bundle / APK** for release.

This environment does not contain the Android SDK/Gradle build tools, so a signed APK cannot be honestly claimed as built here. The project is release-ready source and is ready to build in Android Studio. This environment does not include the Android SDK/Gradle toolchain, so no APK/AAB is falsely represented as already built.

## Release checklist
1. Deploy the backend to a stable HTTPS production hostname.
2. Configure `FRONTEND_ORIGIN` and Paystack production credentials on the server.
3. Test signup, login, stock, wallet funding, payment verification and PIN purchase in Paystack test mode.
4. Configure Paystack webhooks on the backend.
5. Create your own release keystore and generate a signed AAB for Google Play.
6. Before public release, complete Play Console privacy/data-safety declarations and test on physical Android devices.
