<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown, splitTextAndCodeBlocks, type TextPart } from '../../utils/textParser'

const props = defineProps<{ content: string }>()

const parts = computed(() => splitTextAndCodeBlocks(props.content || '').map(enrichPart))

interface EnrichedPart extends TextPart {
  display: string
  isJson: boolean
}

/**
 * 对 JSON 代码块做格式化，便于阅读。
 */
function enrichPart(part: TextPart): EnrichedPart {
  const lang = (part.language || '').toLowerCase()
  const looksJson = lang === 'json' || part.artifactType === 'json' || looksLikeJson(part.content)
  if (!looksJson) {
    return { ...part, display: part.content, isJson: false }
  }
  try {
    const parsed = JSON.parse(part.content)
    return {
      ...part,
      language: part.language || 'json',
      display: JSON.stringify(parsed, null, 2),
      isJson: true,
    }
  } catch {
    return { ...part, display: part.content, isJson: false }
  }
}

function looksLikeJson(text: string): boolean {
  const t = text.trim()
  return (t.startsWith('{') && t.endsWith('}')) || (t.startsWith('[') && t.endsWith(']'))
}

/**
 * 代码 / 工件语言标签。
 */
function fenceLabel(part: EnrichedPart): string {
  if (part.type === 'artifact') return part.artifactType || 'artifact'
  if (part.isJson) return 'json'
  if (part.language && part.language !== 'text') return part.language
  return 'code'
}

/**
 * 复制单个代码块。
 */
function copyPart(text: string) {
  if (text) navigator.clipboard?.writeText(text)
}

/**
 * 下载代码/工件为本地文件。
 */
function downloadPart(part: EnrichedPart) {
  const ext = part.isJson || part.language === 'json'
    ? 'json'
    : part.language && part.language !== 'text'
      ? part.language.replace(/[^a-z0-9]+/gi, '')
      : 'txt'
  const blob = new Blob([part.display], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `result.${ext || 'txt'}`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="message-parts">
    <template v-for="(part, index) in parts" :key="index">
      <div v-if="part.type === 'text'" class="md-block" v-html="renderMarkdown(part.content)" />

      <div v-else-if="part.type === 'artifact'" class="artifact-card" :class="{ json: part.isJson }">
        <div class="artifact-head">
          <span class="artifact-badge">工件 · {{ fenceLabel(part) }}</span>
          <div class="part-actions">
            <el-tooltip content="复制" placement="top" :show-after="200">
              <button class="icon-btn" type="button" aria-label="复制工件" @click="copyPart(part.display)">
                <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true">
                  <rect x="5.5" y="5.5" width="8" height="8" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.4" />
                  <path d="M3.5 10.5V3.5h7" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                </svg>
              </button>
            </el-tooltip>
            <el-tooltip content="下载" placement="top" :show-after="200">
              <button class="icon-btn" type="button" aria-label="下载工件" @click="downloadPart(part)">
                <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true">
                  <path d="M8 2.5v7.2M5.2 7.5 8 10.3l2.8-2.8M3.5 13h9" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
            </el-tooltip>
          </div>
        </div>
        <pre class="code-block"><code>{{ part.display }}</code></pre>
      </div>

      <div v-else class="code-wrap" :class="{ json: part.isJson }">
        <div class="code-head">
          <span class="code-lang">{{ fenceLabel(part) }}</span>
          <div class="part-actions">
            <el-tooltip content="复制" placement="top" :show-after="200">
              <button class="icon-btn" type="button" aria-label="复制代码" @click="copyPart(part.display)">
                <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true">
                  <rect x="5.5" y="5.5" width="8" height="8" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.4" />
                  <path d="M3.5 10.5V3.5h7" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                </svg>
              </button>
            </el-tooltip>
            <el-tooltip content="下载" placement="top" :show-after="200">
              <button class="icon-btn" type="button" aria-label="下载代码" @click="downloadPart(part)">
                <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true">
                  <path d="M8 2.5v7.2M5.2 7.5 8 10.3l2.8-2.8M3.5 13h9" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
            </el-tooltip>
          </div>
        </div>
        <pre class="code-block"><code>{{ part.display }}</code></pre>
      </div>
    </template>
  </div>
</template>

<style scoped>
.message-parts { min-width: 0; width: fit-content; max-width: 100%; font-size: 14px; }
.md-block { min-width: 0; width: fit-content; max-width: 100%; font-size: 14px; }
.md-block :deep(p) { margin: 0 0 0.55em; line-height: 1.7; }
.md-block :deep(p:last-child) { margin-bottom: 0; }
.md-block :deep(.table-scroll) {
  overflow-x: auto;
  margin: 10px 0;
  max-width: min(100%, 840px);
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: #fff;
}
.md-block :deep(table) { border-collapse: collapse; width: 100%; font-size: 14px; }
.md-block :deep(th),
.md-block :deep(td) { border-bottom: 1px solid var(--c-border); padding: 8px 10px; text-align: left; vertical-align: top; }
.md-block :deep(th) { background: #f3f6f6; font-weight: 650; }
.md-block :deep(tr:last-child td) { border-bottom: 0; }
.md-block :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 3px solid var(--c-primary);
  color: var(--c-text-secondary);
  background: #f7fafa;
}
.md-block :deep(ul),
.md-block :deep(ol) { margin: 8px 0; padding-left: 1.3em; }
.md-block :deep(li) { margin: 3px 0; line-height: 1.6; }
.md-block :deep(h1),
.md-block :deep(h2),
.md-block :deep(h3) { margin: 12px 0 6px; line-height: 1.35; font-weight: 700; }
.md-block :deep(h2) {
  margin-top: 14px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--c-border-soft, #e9efef);
  font-size: 15px;
  color: var(--c-text);
}
.md-block :deep(h2:first-child) { margin-top: 0; }
.md-block :deep(ol) {
  margin: 8px 0 10px;
  padding-left: 1.35em;
}
.md-block :deep(ol li) {
  margin: 6px 0;
  padding-left: 2px;
  line-height: 1.65;
}
.md-block :deep(a) { color: var(--c-primary); text-decoration: underline; text-underline-offset: 2px; }
.md-block :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: #eef3f3;
  font-size: 0.92em;
}
.md-block :deep(pre code) { padding: 0; background: transparent; }
.code-wrap { margin-top: 10px; border: 1px solid #2a3842; border-radius: var(--radius); overflow: hidden; }
.code-wrap:first-child { margin-top: 0; }
.code-head, .artifact-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px 6px 10px;
  background: #243039;
}
.part-actions { display: inline-flex; gap: 2px; }
.code-lang, .artifact-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(255,255,255,.08);
  color: #9fb3bd;
  font-size: 10px;
  font-weight: 650;
  text-transform: lowercase;
}
.artifact-badge { text-transform: none; color: #8fd0c8; background: rgba(143,208,200,.12); }
.artifact-card {
  margin-top: 10px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  overflow: hidden;
  background: #f7fafa;
}
.artifact-card:first-child { margin-top: 0; }
.artifact-card .artifact-head { background: #eef5f5; }
.artifact-card .artifact-badge { background: var(--c-primary-soft); color: var(--c-primary); }
.artifact-card .icon-btn { color: var(--c-text-muted); }
.artifact-card .code-block { border-radius: 0; }
.code-block {
  margin: 0;
  padding: 12px 14px;
  overflow-x: auto;
  max-height: 420px;
  background: #1e2a33;
  color: #e8efef;
  white-space: pre;
  font-size: 12.5px;
  line-height: 1.55;
}
.json .code-block { color: #d7ece8; }
.icon-btn {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #9fb3bd;
  cursor: pointer;
}
.icon-btn:hover { color: #fff; background: rgba(255,255,255,.1); }
.artifact-card .icon-btn:hover { color: var(--c-primary); background: var(--c-primary-soft); }
</style>
