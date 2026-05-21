/* global firebase */

/*
 * Firebase Messaging service worker template.
 *
 * Vite does not expose import.meta.env inside files served directly from
 * public/. Run `npm run generate:firebase-sw` to replace these placeholders
 * from .env and .env.local before dev/build.
 */
const firebaseConfig = {
  apiKey: '__VITE_FIREBASE_API_KEY__',
  appId: '__VITE_FIREBASE_APP_ID__',
  authDomain: '__VITE_FIREBASE_AUTH_DOMAIN__',
  messagingSenderId: '__VITE_FIREBASE_MESSAGING_SENDER_ID__',
  projectId: '__VITE_FIREBASE_PROJECT_ID__',
}

function hasFirebaseConfig(config) {
  return Object.values(config).every(
    (value) => typeof value === 'string' && value && !value.startsWith('__'),
  )
}

importScripts('https://www.gstatic.com/firebasejs/12.13.0/firebase-app-compat.js')
importScripts(
  'https://www.gstatic.com/firebasejs/12.13.0/firebase-messaging-compat.js',
)

if (hasFirebaseConfig(firebaseConfig)) {
  firebase.initializeApp(firebaseConfig)

  const messaging = firebase.messaging()

  messaging.onBackgroundMessage((payload) => {
    const title = payload.data?.title || payload.notification?.title || 'SynChat'
    const options = {
      body: payload.data?.body || payload.notification?.body,
      data: payload.data || {},
    }

    self.registration.showNotification(title, options)
  })
} else {
  console.warn(
    'Firebase Messaging service worker is not configured. Generate public/firebase-messaging-sw.js with Firebase env values before enabling push notifications.',
  )
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close()

  const clickAction = event.notification.data?.clickAction || '/'
  const targetUrl = new URL(clickAction, self.location.origin).href

  event.waitUntil(
    self.clients
      .matchAll({ includeUncontrolled: true, type: 'window' })
      .then((clients) => {
        for (const client of clients) {
          if (new URL(client.url).origin === self.location.origin) {
            client.focus()
            client.navigate(targetUrl)
            return
          }
        }

        return self.clients.openWindow(targetUrl)
      }),
  )
})
