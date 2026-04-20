import { onMounted, onUnmounted, ref, type Ref } from 'vue'

/** Reactive `window.matchMedia` (browser only). */
export function useMediaQuery(query: string): Ref<boolean> {
  const matches = ref(false)
  let mql: MediaQueryList | null = null

  function sync(): void {
    if (mql) matches.value = mql.matches
  }

  onMounted(() => {
    mql = window.matchMedia(query)
    matches.value = mql.matches
    mql.addEventListener('change', sync)
  })

  onUnmounted(() => {
    mql?.removeEventListener('change', sync)
    mql = null
  })

  return matches
}
