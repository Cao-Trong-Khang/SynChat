import { AppBootstrap } from './AppBootstrap'
import { AuthenticatedAppEffects } from './AuthenticatedAppEffects'
import { AppProviders } from './providers'

export function App() {
  return (
    <AppProviders>
      <AuthenticatedAppEffects />
      <AppBootstrap />
    </AppProviders>
  )
}

export default App
