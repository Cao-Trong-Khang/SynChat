import { websocketManager } from '@/features/realtime/websocketManager'

export const deviceStateService = {
  markBackground() {
    if (!websocketManager.isConnected()) {
      return
    }

    try {
      websocketManager.sendJson({
        payload: { state: 'background' },
        requestId: crypto.randomUUID(),
        type: 'device.state',
      })
    } catch (error) {
      console.warn('Failed to send device background state.', error)
    }
  },
}
