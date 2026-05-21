import { ChevronLeft, ChevronRight, MessageCircle, RefreshCw, Users, Zap } from 'lucide-react'
import { useState } from 'react'

const slides = [
  {
    description: 'A clean, realtime chat experience for staying connected.',
    Icon: MessageCircle,
    title: 'Welcome to SynChat',
  },
  {
    description:
      'Send and receive messages instantly with WebSocket-powered updates.',
    Icon: Zap,
    title: 'Chat in realtime',
  },
  {
    description:
      'Keep conversations available locally and sync missed events when you reconnect.',
    Icon: RefreshCw,
    title: 'Offline-first message sync',
  },
  {
    description:
      'See when people are online, typing, and when your messages are delivered or read.',
    Icon: Users,
    title: 'Presence, typing, read receipts',
  },
]

export function LandingView() {
  const [activeSlideIndex, setActiveSlideIndex] = useState(0)
  const activeSlide = slides[activeSlideIndex]
  const Icon = activeSlide.Icon

  function showPreviousSlide() {
    setActiveSlideIndex((current) =>
      current === 0 ? slides.length - 1 : current - 1,
    )
  }

  function showNextSlide() {
    setActiveSlideIndex((current) =>
      current === slides.length - 1 ? 0 : current + 1,
    )
  }

  return (
    <section className="flex h-full items-center justify-center p-8">
      <div className="w-full max-w-md rounded-lg border border-zinc-200 bg-white p-8 text-center shadow-sm">
        <div className="mx-auto flex size-16 items-center justify-center rounded-full bg-blue-50 text-[#005ae0] transition-colors">
          <Icon aria-hidden="true" className="size-8" />
        </div>

        <div className="mt-6 overflow-hidden">
          <div
            className="flex transition-transform duration-300 ease-out"
            style={{ transform: `translateX(-${activeSlideIndex * 100}%)` }}
          >
            {slides.map((slide) => (
              <div className="w-full shrink-0 px-1" key={slide.title}>
                <h1 className="text-3xl font-semibold tracking-normal text-zinc-950">
                  {slide.title}
                </h1>
                <p className="mt-3 text-sm leading-6 text-zinc-500">
                  {slide.description}
                </p>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-8 flex items-center justify-center gap-2">
          {slides.map((slide, index) => (
            <button
              aria-label={`Show ${slide.title}`}
              className={
                index === activeSlideIndex
                  ? 'h-2.5 w-6 rounded-full bg-[#005ae0] transition-all'
                  : 'size-2.5 rounded-full bg-zinc-200 transition-all hover:bg-zinc-300'
              }
              key={slide.title}
              onClick={() => setActiveSlideIndex(index)}
              type="button"
            />
          ))}
        </div>

        <div className="mt-8 flex items-center justify-center gap-3">
          <button
            aria-label="Previous slide"
            className="inline-flex size-10 items-center justify-center rounded-full border border-zinc-200 text-zinc-600 transition-colors hover:bg-zinc-50 hover:text-zinc-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#005ae0]"
            onClick={showPreviousSlide}
            type="button"
          >
            <ChevronLeft aria-hidden="true" className="size-5" />
          </button>
          <button
            aria-label="Next slide"
            className="inline-flex size-10 items-center justify-center rounded-full bg-[#005ae0] text-white transition-colors hover:bg-[#004bb8] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#005ae0]"
            onClick={showNextSlide}
            type="button"
          >
            <ChevronRight aria-hidden="true" className="size-5" />
          </button>
        </div>
      </div>
    </section>
  )
}
