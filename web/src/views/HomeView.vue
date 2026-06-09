<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'

const health = ref<string>('checking...')

onMounted(async () => {
  try {
    const res = await axios.get('/api/actuator/health')
    health.value = res.data.status
  } catch {
    health.value = 'unreachable'
  }
})
</script>

<template>
  <main class="min-h-screen flex items-center justify-center p-8">
    <section class="max-w-xl text-center">
      <h1 class="text-4xl font-bold text-brand-700">Eunomia</h1>
      <p class="mt-3 text-slate-600">
        AI-powered banking compliance engine. Sprint 0 scaffold.
      </p>
      <div class="mt-8 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <p class="text-sm text-slate-500">Backend health</p>
        <p class="mt-1 font-mono text-lg" :class="health === 'UP' ? 'text-emerald-600' : 'text-amber-600'">
          {{ health }}
        </p>
      </div>
    </section>
  </main>
</template>
