export async function registerFirebaseMessagingServiceWorker() {
  if (!('serviceWorker' in navigator)) {
    return null
  }

  try {
    return await navigator.serviceWorker.register('/firebase-messaging-sw.js')
  } catch (error) {
    console.warn('Failed to register Firebase Messaging service worker.', error)
    return null
  }
}
