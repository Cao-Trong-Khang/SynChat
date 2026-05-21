export type FirebaseNotificationConfig = {
  apiKey: string
  appId: string
  authDomain: string
  messagingSenderId: string
  projectId: string
  vapidKey: string
}

export type NotificationRegistrationState = {
  lastError: null | string
  permission: NotificationPermission | 'unsupported'
  registering: boolean
  token: null | string
}
