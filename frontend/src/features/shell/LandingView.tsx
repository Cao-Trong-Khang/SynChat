import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'

import presenceIllustration from '@/assets/landing/presence.png'
import realtimeIllustration from '@/assets/landing/realtime.png'
import syncIllustration from '@/assets/landing/sync.png'
import welcomeIllustration from '@/assets/landing/welcome.png'

const slides = [
  {
    description: 'A clean, realtime chat experience for staying connected.',
    image: welcomeIllustration,
    title: 'Welcome to SynChat',
  },
  {
    description:
      'Send and receive messages instantly with WebSocket-powered updates.',
    image: realtimeIllustration,
    title: 'Chat in realtime',
  },
  {
    description:
      'Keep conversations available locally and sync missed events when you reconnect.',
    image: syncIllustration,
    title: 'Offline-first message sync',
  },
  {
    description:
      'See when people are online, typing, and when your messages are delivered or read.',
    image: presenceIllustration,
    title: 'Presence, typing, read receipts',
  },
]

export function LandingView() {
  const [activeSlideIndex, setActiveSlideIndex] = useState(0)

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setActiveSlideIndex((current) =>
        current === slides.length - 1 ? 0 : current + 1,
      )
    }, 3_500)

    return () => window.clearInterval(intervalId)
  }, [])

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
    <section className="flex h-full items-center justify-center bg-zinc-50 p-8">
      <div className="w-full max-w-2xl rounded-2xl border border-zinc-200 bg-white px-8 py-10 text-center shadow-sm">
        <div className="overflow-hidden">
          <div
            className="flex transition-transform duration-300 ease-out"
            style={{ transform: `translateX(-${activeSlideIndex * 100}%)` }}
          >
            {slides.map((slide) => (
              <div
                className="flex w-full shrink-0 flex-col items-center px-1"
                key={slide.title}
              >
                <div className="flex aspect-[4/3] w-full max-w-sm items-center justify-center rounded-3xl bg-blue-50/70 p-6 shadow-inner">
                  <img
                    alt=""
                    className="max-h-full w-full object-contain drop-shadow-sm"
                    draggable={false}
                    src={slide.image}
                  />
                </div>
                <h1 className="mt-8 text-3xl font-semibold tracking-normal text-zinc-950">
                  {slide.title}
                </h1>
                <p className="mt-3 max-w-md text-sm leading-6 text-zinc-500">
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
