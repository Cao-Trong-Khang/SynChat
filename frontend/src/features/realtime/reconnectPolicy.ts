const RECONNECT_DELAYS_MS = [1_000, 2_000, 5_000, 10_000, 30_000]

let attempt = 0

export const reconnectPolicy = {
  nextDelay() {
    const delay =
      RECONNECT_DELAYS_MS[Math.min(attempt, RECONNECT_DELAYS_MS.length - 1)]
    attempt += 1
    return delay
  },

  reset() {
    attempt = 0
  },

  currentAttempt() {
    return attempt
  },
}
