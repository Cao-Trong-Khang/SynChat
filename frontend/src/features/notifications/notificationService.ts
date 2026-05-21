import { getToken } from 'firebase/messaging'

import { getOrCreateDeviceId } from '@/features/device/deviceId'
import { sendCommand } from '@/features/realtime/commandSender'
import { websocketManager } from '@/features/realtime/websocketManager'

import {
  getFirebaseVapidKey,
  getMessagingInstance,
  getMissingFirebaseConfigKeys,
} from './firebaseMessaging'
import { useNotificationStore } from './notificationStore'
import { registerFirebaseMessagingServiceWorker } from './serviceWorkerRegistration'

const initializedSessionIds = new Set<number>()
let backendRegisteredToken: null | string = null

function supportsNotificationRuntime() {
  return (
    typeof Notification !== 'undefined' &&
    typeof navigator !== 'undefined' &&
    'serviceWorker' in navigator
  )
}

async function warnIfServiceWorkerHasPlaceholders() {
  try {
    const response = await fetch('/firebase-messaging-sw.js', {
      cache: 'no-store',
    })
    const serviceWorkerText = await response.text()

    if (serviceWorkerText.includes('__VITE_FIREBASE_')) {
      console.warn(
        'Firebase Messaging service worker still contains config placeholders. Replace them before enabling push notifications.',
      )
    }
  } catch (error) {
    console.warn('Could not inspect Firebase Messaging service worker.', error)
  }
}

async function registerPushTokenWithBackend(pushToken: string) {
  if (!websocketManager.isConnected()) {
    return false
  }

  if (backendRegisteredToken === pushToken) {
    return true
  }

  console.log('Registering push token with backend...')
  sendCommand('devices.register', {
    deviceId: getOrCreateDeviceId(),
    platform: 'web',
    pushProvider: 'fcm_web',
    pushToken,
  })
  backendRegisteredToken = pushToken

  return true
}

export const notificationService = {
  async initializeAfterLogin(sessionId: number) {
    if (initializedSessionIds.has(sessionId)) {
      return
    }

    initializedSessionIds.add(sessionId)
    console.log('Initializing notifications after login...')

    const store = useNotificationStore.getState()

    if (!supportsNotificationRuntime()) {
      store.markUnsupported('Notifications or service workers are unsupported.')
      return
    }

    const missingFirebaseConfigKeys = getMissingFirebaseConfigKeys()

    if (missingFirebaseConfigKeys.length > 0) {
      const message = `Firebase Messaging is not configured. Missing env: ${missingFirebaseConfigKeys.join(', ')}`
      console.warn(message)
      store.setLastError(message)
      return
    }

    let permission = Notification.permission
    store.setPermission(permission)

    if (permission === 'default') {
      console.log('Requesting notification permission...')
      permission = await Notification.requestPermission()
      console.log('Notification permission:', permission)
      store.setPermission(permission)
    } else {
      console.log('Notification permission:', permission)
    }

    if (permission === 'denied' || permission === 'default') {
      return
    }

    store.setRegistering(true)
    store.setLastError(null)

    try {
      console.log('Registering Firebase messaging service worker...')
      await warnIfServiceWorkerHasPlaceholders()
      const serviceWorkerRegistration =
        await registerFirebaseMessagingServiceWorker()

      if (!serviceWorkerRegistration) {
        store.markUnsupported('Firebase messaging service worker unavailable.')
        return
      }

      console.log('Firebase messaging service worker registered')

      const messaging = await getMessagingInstance()
      const vapidKey = getFirebaseVapidKey()

      if (!messaging || !vapidKey) {
        const message =
          'Firebase Messaging could not initialize. Check Firebase web config and service worker config.'
        console.warn(message)
        store.setLastError(message)
        return
      }

      console.log('Requesting FCM token...')
      const token = await getToken(messaging, {
        serviceWorkerRegistration,
        vapidKey,
      })

      if (!token) {
        store.setLastError('Firebase returned an empty messaging token.')
        return
      }

      console.log('FCM token acquired')
      store.setToken(token)
      await notificationService.registerStoredTokenWithBackend()
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : 'Failed to initialize notifications.'
      console.warn('Failed to initialize notifications.', error)
      store.setLastError(message)
    } finally {
      useNotificationStore.getState().setRegistering(false)
    }
  },

  registerStoredTokenWithBackend() {
    const token = useNotificationStore.getState().token

    if (!token) {
      return false
    }

    return registerPushTokenWithBackend(token)
  },

  clearBackendRegistrationForLogout() {
    backendRegisteredToken = null

    if (!websocketManager.isConnected()) {
      return
    }

    try {
      sendCommand('devices.register', {
        deviceId: getOrCreateDeviceId(),
        platform: 'web',
        pushProvider: null,
        pushToken: null,
      })
    } catch {
      // Logout must not be blocked by push-token cleanup.
    }
  },

  resetRuntime() {
    initializedSessionIds.clear()
    backendRegisteredToken = null
  },
}
