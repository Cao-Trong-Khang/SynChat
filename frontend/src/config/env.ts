const requiredEnv = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL,
  wsUrl: import.meta.env.VITE_WS_URL,
}

for (const [key, value] of Object.entries(requiredEnv)) {
  if (!value) {
    throw new Error(`Missing required environment variable for env.${key}`)
  }
}

export const env = requiredEnv as {
  apiBaseUrl: string
  wsUrl: string
}
