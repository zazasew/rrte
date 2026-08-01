# YouTube instant push: one unavoidable external step

The Android project no longer schedules periodic YouTube polling. That is intentional: periodic polling costs battery and cannot be truly instant.

A real push path is:

YouTube upload -> server/WebSub subscriber -> Firebase Cloud Messaging (FCM) -> Android -> notification

There is no way to make the FCM part fully self-contained in a GitHub ZIP. Firebase requires an app/project identity and server credentials belonging to the app owner. Those credentials must not be hard-coded into a public repository.

To finish instant push, the repository needs:
- a Firebase Android app (`google-services.json`),
- Firebase Messaging in the Android app,
- a small backend with the Firebase Admin credential,
- a public HTTPS endpoint for YouTube WebSub callbacks.

Without those owner-specific credentials/hosting, any ZIP claiming to provide truly working instant push notifications would be misleading.

For now the old periodic YouTube worker remains in source only for migration/compatibility, but WorkScheduler explicitly cancels it and never schedules it.
