import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(scriptDir, '..')
const templatePath = resolve(
  projectRoot,
  'public',
  'firebase-messaging-sw.template.js',
)
const outputPath = resolve(projectRoot, 'public', 'firebase-messaging-sw.js')

const requiredEnvKeys = [
  'VITE_FIREBASE_API_KEY',
  'VITE_FIREBASE_APP_ID',
  'VITE_FIREBASE_AUTH_DOMAIN',
  'VITE_FIREBASE_MESSAGING_SENDER_ID',
  'VITE_FIREBASE_PROJECT_ID',
]

function parseEnvFile(path) {
  if (!existsSync(path)) {
    return {}
  }

  const result = {}
  const lines = readFileSync(path, 'utf8').split(/\r?\n/)

  for (const line of lines) {
    const trimmedLine = line.trim()

    if (!trimmedLine || trimmedLine.startsWith('#')) {
      continue
    }

    const equalsIndex = trimmedLine.indexOf('=')

    if (equalsIndex === -1) {
      continue
    }

    const key = trimmedLine.slice(0, equalsIndex).trim()
    let value = trimmedLine.slice(equalsIndex + 1).trim()

    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1)
    }

    result[key] = value
  }

  return result
}

const env = {
  ...parseEnvFile(resolve(projectRoot, '.env')),
  ...parseEnvFile(resolve(projectRoot, '.env.local')),
}
const missingKeys = requiredEnvKeys.filter((key) => !env[key])

if (missingKeys.length > 0) {
  throw new Error(
    `Missing Firebase service worker env values: ${missingKeys.join(', ')}. Add them to frontend/.env or frontend/.env.local.`,
  )
}

let serviceWorkerSource = readFileSync(templatePath, 'utf8')

for (const key of requiredEnvKeys) {
  serviceWorkerSource = serviceWorkerSource.replaceAll(`__${key}__`, env[key])
}

writeFileSync(outputPath, serviceWorkerSource)
console.log('Generated public/firebase-messaging-sw.js')
