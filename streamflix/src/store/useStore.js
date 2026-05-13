import { create } from 'zustand'

const useStore = create((set) => ({
  searchOpen: false,
  searchQuery: '',
  setSearchOpen: (open) => set({ searchOpen: open }),
  setSearchQuery: (query) => set({ searchQuery: query }),

  allVideos: [],
  setAllVideos: (videos) => set({ allVideos: videos }),

  scrolledPast: false,
  setScrolledPast: (val) => set({ scrolledPast: val }),

  activeHover: null,
  setActiveHover: (id) => set({ activeHover: id }),
  clearHover: () => set({ activeHover: null }),

  allItems: {},
  setAllItems: (items) => {
    if (!Array.isArray(items)) return
    set((state) => {
      const next = { ...state.allItems }
      items.forEach((i) => { next[i.id || i.videoId] = i })
      return { allItems: next }
    })
  },

  continueWatching: [],
  setContinueWatching: (items) => set({ continueWatching: items }),
  addToContinueWatching: (item) =>
    set((state) => {
      const exists = state.continueWatching.find((i) => i.id === item.id)
      if (exists) return state
      return { continueWatching: [item, ...state.continueWatching].slice(0, 20) }
    }),
  updateProgress: (id, progress) =>
    set((state) => ({
      continueWatching: state.continueWatching.map((i) =>
        i.id === id ? { ...i, progress } : i
      ),
    })),
}))

export default useStore
