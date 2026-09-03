# IB-TECH ePINs production deployment

## Current architecture
- Public website: `https://ibtech.com`
- Current API: `https://ibtech-epins.onrender.com`
- Android app: native Kotlin/Jetpack Compose

## Recommended final architecture
Use `https://api.ibtech.com` for the Node API and point the Android `BuildConfig.IBTECH_API_BASE_URL` to it after the custom domain is configured on the Node host. Do **not** point the app at the FreeHosting website hostname unless that server is actually running the IB-TECH Node API.

## Release build
1. Open the project in current Android Studio.
2. Confirm `BuildConfig.IBTECH_API_BASE_URL` is the intended HTTPS API.
3. Test login, signup, wallet funding, payment verification, PIN purchase, and transaction history against Paystack test mode first.
4. Create your own release keystore. Never commit it to source control.
5. Build a signed Android App Bundle (AAB) for Google Play.

## Secrets
The Paystack secret key belongs only on the backend. It must never be placed in `MainActivity.kt`, Gradle files, resources, or the APK.
