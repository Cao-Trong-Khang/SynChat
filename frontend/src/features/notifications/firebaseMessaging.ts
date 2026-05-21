import { getApp, getApps, initializeApp, type FirebaseApp } from 'firebase/app'
import {
  getMessaging,
  isSupported,
  type Messaging,
} from 'firebase/messaging'

import type { FirebaseNotificationConfig } from './notificationTypes'

let messagingInstance: Messaging | null | undefined

function readFirebaseConfig(): FirebaseNotificationConfig | null {
  const config = {
    apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
    appId: import.meta.env.VITE_FIREBASE_APP_ID,
    authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
    messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
    projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
    vapidKey: import.meta.env.VITE_FIREBASE_VAPID_KEY,
  }

  if (Object.values(config).some((value) => !value)) {
    return null
  }

  return config
}

function getFirebaseApp(config: FirebaseNotificationConfig): FirebaseApp {
  return getApps().length > 0
    ? getApp()
    : initializeApp({
        apiKey: config.apiKey,
        appId: config.appId,
        authDomain: config.authDomain,
        messagingSenderId: config.messagingSenderId,
        projectId: config.projectId,
      })
}

export async function getMessagingInstance() {
  if (messagingInstance !== undefined) {
    return messagingInstance
  }

  const config = readFirebaseConfig()

  if (!config) {
    messagingInstance = null
    return null
  }

  try {
    if (!(await isSupported())) {
      messagingInstance = null
      return null
    }

    messagingInstance = getMessaging(getFirebaseApp(config))
    return messagingInstance
  } catch (error) {
    console.warn('Firebase Messaging is not available.', error)
    messagingInstance = null
    return null
  }
}

export function getFirebaseVapidKey() {
  return import.meta.env.VITE_FIREBASE_VAPID_KEY || null
}
