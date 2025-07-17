export function useCounter() {
  const counter = ref<number | undefined>(undefined);

  async function updateCount(count: number) {
    counter.value = count;
  }

  return { counter, updateCount };
}
