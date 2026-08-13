<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown, splitTextAndCodeBlocks, type TextPart } from '../../utils/textParser'

const props = defineProps<{ content: string }>()

const parts = computed(() => splitTextAndCodeBlocks(props.content || ''))

/**
 * 代码 / 工件语言标签。
 */
function fenceLabel(part: TextPart): string {
  if (part.type === 'artifact') return part.artifactType || 'artifact'
  if (part.language && part.language !== 'text') return part.language
  return ''
}

/**
 * 复制单个代码块。
 */
function copyPart(text: string) {
  if (text) navigator.clipboard?.writeText(text)
}
</script>

<template>
  <div class="message-parts">
    <template v-for="(part, index) in parts" :key="index">
      <div v-if="part.type === 'text'" class="md-block" v-html="renderMarkdown(part.content)" />

      <div v-else-if="part.type === 'artifact'" class="artifact-card">
        <div class="artifact-head">
          <span class="artifact-badge">工件 · {{ fenceLabel(part) }}</span>
          <el-tooltip content="复制" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="复制工件" @click="copyPart(part.content)">
              <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true">
                <rect x="5.5" y="5.5" width="8" height="8" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.4" />
                <path d="M3.5 10.5V3.5h7" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
              </svg>
            </button>
          </el-tooltip>
        </div>
        <pre class="code-block"><code>{{ part.content }}</code></pre>
      </div>

      <div v-else class="code-wrap">
        <div class="code-head">
          <span v-if="fenceLabel(part)" class="code-lang">{{ fenceLabel(part) }}</span>
          <el-tooltip content="复制" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="复制代码" @click="copyPart(part.content)">
              <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true">
                <rect x="5.5" y="5.5" width="8" height="8" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.4" />
                <path d="M3.5 10.5V3.5h7" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
              </svg>
            </button>
          </el-tooltip>
        </div>
        <pre class="code-block"><code>{{ part.content }}</code></pre>
      </div>
    </template>
  </div>
</template>

<style scoped>
.message-parts { min-width: 0; width: fit-content; max-width: 100%; }
.md-block { min-width: 0; width: fit-content; max-width: 100%; }
.md-block :deep(p) { margin: 0 0 0.45em; }
.md-block :deep(p:last-child) { margin-bottom: 0; }
.md-block :deep(.table-scroll) { overflow-x: auto; margin: 8px 0; max-width: min(100%, 640px); }
.md-block :deep(table) { border-collapse: collapse; width: 100%; font-size: 13px; }
.md-block :deep(th),
.md-block :deep(td) { border: 1px solid var(--c-border); padding: 6px 8px; text-align: left; }
.md-block :deep(th) { background: #f3f6f6; }
.code-wrap { margin-top: 10px; }
.code-wrap:first-child { margin-top: 0; }
.code-head, .artifact-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.code-lang, .artifact-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  background: #eef2f2;
  color: var(--c-text-muted);
  font-size: 10px;
  font-weight: 650;
  text-transform: lowercase;
}
.artifact-badge { text-transform: none; color: var(--c-primary); background: var(--c-primary-soft); }
.artifact-card {
  margin-top: 10px;
  padding: 10px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: #f7fafa;
}
.artifact-card:first-child { margin-top: 0; }
.code-block {
  margin: 0;
  padding: 12px;
  overflow-x: auto;
  border-radius: var(--radius);
  background: #1e2a33;
  color: #e8efef;
  white-space: pre;
}
.icon-btn {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--c-text-muted);
  cursor: pointer;
}
.icon-btn:hover { color: var(--c-primary); background: var(--c-primary-soft); }
</style>
