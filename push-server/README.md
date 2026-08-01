# TheBrief YouTube push backend

This is the backend shape needed for truly immediate YouTube upload notifications:

YouTube WebSub -> HTTPS callback -> Firebase Cloud Messaging -> Android.

The server intentionally contains no credentials. A deployment must supply a
Firebase service account through the hosting platform. The Android app likewise
needs the Firebase project configuration (`google-services.json`).

This is an unavoidable ownership/deployment requirement; it cannot safely be
pre-filled in a public GitHub ZIP.
