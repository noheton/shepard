/**
 * Returns current route fragment as string.
 *
 * The url: `localhost:3000/about#test` returns *test*.
 * @returns {currentFragment : string}
 */
export function useRouteFragment() {
  const router = useRouter();
  const route = useRoute();

  const initialRoute = router.currentRoute.value;
  const initialFragment = initialRoute.hash.slice(1);

  const routeFragment = ref<string>(initialFragment ?? "");

  watch(
    () => route.hash,
    () => {
      routeFragment.value = route.hash.slice(1) ?? "";
    },
  );

  return { routeFragment };
}
