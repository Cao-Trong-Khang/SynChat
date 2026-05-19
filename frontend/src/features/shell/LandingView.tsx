export function LandingView() {
  return (
    <section className="flex h-full items-center justify-center p-8">
      <div className="max-w-md rounded-lg border border-zinc-800 bg-zinc-900/70 p-8 text-center shadow-2xl shadow-black/20">
        <p className="text-sm font-medium uppercase tracking-wide text-zinc-500">
          SynChat Web
        </p>
        <h1 className="mt-3 text-3xl font-semibold tracking-normal text-zinc-50">
          Welcome to SynChat
        </h1>
        <p className="mt-3 text-sm leading-6 text-zinc-400">
          Select a conversation when your inbox is ready, or search for someone
          to start chatting.
        </p>
      </div>
    </section>
  )
}
