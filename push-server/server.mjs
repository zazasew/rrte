import express from 'express';
import admin from 'firebase-admin';

/*
 * Production shape for instant YouTube notifications:
 * YouTube WebSub -> this HTTPS server -> FCM -> Android.
 *
 * This server intentionally does not contain Firebase credentials. Provide
 * GOOGLE_APPLICATION_CREDENTIALS or a platform-managed service account when
 * deploying it. Never commit those credentials to GitHub.
 */
if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();
const app = express();
app.use(express.json({ limit: '256kb' }));

app.get('/health', (_req, res) => res.json({ ok: true }));

// Android app registers its FCM token and the channel IDs it follows.
app.post('/v1/devices/register', async (req, res) => {
  const { token, channelIds } = req.body ?? {};
  if (typeof token !== 'string' || !token || !Array.isArray(channelIds)) {
    return res.status(400).json({ error: 'token and channelIds are required' });
  }
  await db.collection('devices').doc(token).set({
    token,
    channelIds: channelIds.filter(x => typeof x === 'string'),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  res.json({ ok: true });
});

// YouTube WebSub sends a GET challenge here when a subscription is created.
app.get('/v1/youtube/websub', (req, res) => {
  const challenge = req.query['hub.challenge'];
  if (typeof challenge === 'string') return res.status(200).send(challenge);
  res.sendStatus(400);
});

// WebSub sends an Atom notification. A production deployment should verify
// hub signatures, parse the entry, then call sendNotification().
app.post('/v1/youtube/websub', express.text({ type: ['application/atom+xml', 'application/xml', 'text/xml'] }), async (req, res) => {
  res.sendStatus(204);
  // TODO: parse the Atom entry and extract channelId/videoId/title/link.
  // Then call sendNotification(channelId, payload).
});

async function sendNotification(channelId, payload) {
  const snap = await db.collection('devices').where('channelIds', 'array-contains', channelId).get();
  const tokens = snap.docs.map(d => d.data().token).filter(Boolean);
  if (!tokens.length) return;
  await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title: payload.title, body: 'New video uploaded' },
    data: {
      channelId,
      videoId: payload.videoId,
      url: payload.url
    }
  });
}

const port = Number(process.env.PORT || 8080);
app.listen(port, () => console.log(`TheBrief push server listening on ${port}`));
