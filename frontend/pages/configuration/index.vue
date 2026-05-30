<script setup lang="ts">
// Backward-compat redirect: keeps old /configuration bookmarks working → /admin
await navigateTo("/admin", { replace: true });
</script>
