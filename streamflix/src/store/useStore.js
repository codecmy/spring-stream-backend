import { create } from 'zustand'

const useStore = create((set) => ({
  user: null,
  isAuthenticated: false,
  isAdmin: false,
  authLoading: true,

  checkSession: async () => {
    set({ authLoading: true });
    try {
      const token = localStorage.getItem('token');
      if (!token) {
        set({ user: null, isAuthenticated: false, isAdmin: false, authLoading: false });
        return;
      }
      const res = await fetch('/api/v1/auth/me', {
        headers: { 'Authorization': 'Bearer ' + token }
      });
      if (res.ok) {
        const user = await res.json();
        set({ user, isAuthenticated: true, isAdmin: user.role === 'ADMIN', authLoading: false });
      } else {
        localStorage.removeItem('token');
        set({ user: null, isAuthenticated: false, isAdmin: false, authLoading: false });
      }
    } catch {
      set({ user: null, isAuthenticated: false, isAdmin: false, authLoading: false });
    }
  },

  logout: () => {
    localStorage.removeItem('token');
    set({ user: null, isAuthenticated: false, isAdmin: false });
  },
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
