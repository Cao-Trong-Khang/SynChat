/* global firebase */

/*
 * Firebase Messaging service worker template.
 *
 * Vite does not expose import.meta.env inside files served directly from
 * public/. Run `npm run generate:firebase-sw` to replace these placeholders
 * from .env and .env.local before dev/build.
 */
const firebaseConfig = {
  apiKey: 'AIzaSyBWUii70KVkwFHKwTCzRSgeQlSkAIczjP4',
  appId: '1:672691639294:web:9112709b74174b477566af',
  authDomain: 'synchat-87d8f.firebaseapp.com',
  messagingSenderId: '672691639294',
  projectId: 'synchat-87d8f',
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
    const title = payload.notification?.title || 'SynChat'
    const options = {
      body: payload.notification?.body,
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
