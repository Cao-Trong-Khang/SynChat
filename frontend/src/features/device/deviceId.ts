const DEVICE_ID_STORAGE_KEY = 'synchat.deviceId'

export function getOrCreateDeviceId(): string {
  const existingDeviceId = localStorage.getItem(DEVICE_ID_STORAGE_KEY)

  if (existingDeviceId) {
    return existingDeviceId
  }

  const deviceId = crypto.randomUUID()
  localStorage.setItem(DEVICE_ID_STORAGE_KEY, deviceId)

  return deviceId
}
