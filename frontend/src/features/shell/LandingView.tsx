export function LandingView() {
  return (
    <section className="flex h-full items-center justify-center p-8">
      <div className="max-w-md rounded-lg border border-zinc-200 bg-white p-8 text-center shadow-sm">
        <p className="text-sm font-medium uppercase tracking-wide text-zinc-500">
          SynChat Web
        </p>
        <h1 className="mt-3 text-3xl font-semibold tracking-normal text-zinc-950">
          Welcome to SynChat
        </h1>
        <p className="mt-3 text-sm leading-6 text-zinc-500">
          Select a conversation when your inbox is ready, or search for someone
          to start chatting.
        </p>
      </div>
    </section>
  )
}
