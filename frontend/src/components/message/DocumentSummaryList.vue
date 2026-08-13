<script setup lang="ts">
import type { MessageDocumentSummary } from '../../types'

defineProps<{ summaries: MessageDocumentSummary[] }>()
</script>

<template>
  <div v-if="summaries?.length" class="doc-list">
    <article v-for="doc in summaries" :key="doc.documentId" class="doc-card">
      <div class="doc-icon" aria-hidden="true">
        <svg viewBox="0 0 16 16" width="14" height="14">
          <path d="M4 2.5h5.2L12 5.3V13.5H4V2.5Z" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round" />
          <path d="M9.2 2.5V5.3H12" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round" />
        </svg>
      </div>
      <div class="doc-body">
        <strong class="doc-name">{{ doc.filename }}</strong>
        <p class="doc-summary">{{ doc.summary }}</p>
        <span v-if="doc.pageCount" class="doc-meta">{{ doc.pageCount }} 页</span>
      </div>
    </article>
  </div>
</template>

<style scoped>
.doc-list { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.doc-card {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: #f7fafa;
}
.doc-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--c-primary-soft);
  color: var(--c-primary);
}
.doc-body { min-width: 0; flex: 1; }
.doc-name { display: block; font-size: 13px; font-weight: 650; }
.doc-summary { margin: 4px 0 0; font-size: 12px; line-height: 1.5; color: var(--c-text-secondary); }
.doc-meta { display: inline-block; margin-top: 4px; font-size: 11px; color: var(--c-text-muted); }
</style>
