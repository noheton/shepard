<script setup lang="ts">
// Keep old /user URL working — redirect to /me
await navigateTo("/me", { replace: true });
</script>
