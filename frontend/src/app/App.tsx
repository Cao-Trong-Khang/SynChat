import { AppBootstrap } from './AppBootstrap'
import { AppProviders } from './providers'

export function App() {
  return (
    <AppProviders>
      <AppBootstrap />
    </AppProviders>
  )
}

export default App
