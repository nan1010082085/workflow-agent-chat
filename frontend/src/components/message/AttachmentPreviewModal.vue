<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { MessageAttachment } from '../../types'
import { attachmentContentUrl } from '../../api/client'
import { isImage, isPdf, fileKind, formatSize } from '../../utils/attachmentKind'

const props = defineProps<{
  /** 是否显示预览弹层 */
  modelValue: boolean
  /** 当前预览的附件 */
  attachment: MessageAttachment | null
  /** 图片 gallery 列表（仅图片时用于左右切换） */
  gallery?: MessageAttachment[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'update:attachment', value: MessageAttachment): void
}>()

const isOpen = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

/** 是否为图片类型 */
const isCurrentImage = computed(() => props.attachment ? isImage(props.attachment) : false)

/** 是否为 PDF 类型 */
const isCurrentPdf = computed(() => props.attachment ? isPdf(props.attachment) : false)

/** 图片 gallery（过滤非图片，确保切换时只在图片间跳转） */
const imageGallery = computed(() => {
  const source = props.gallery?.length ? props.gallery : (props.attachment ? [props.attachment] : [])
  return source.filter(isImage)
})

/** 当前图片在 imageGallery 中的索引 */
const imageIndex = computed(() => {
  if (!imageGallery.value.length || !props.attachment) return -1
  return imageGallery.value.findIndex((a) => a.id === props.attachment?.id)
})

/** 是否有上一张图片 */
const hasPrev = computed(() => imageIndex.value > 0)

/** 是否有下一张图片 */
const hasNext = computed(() => imageIndex.value >= 0 && imageIndex.value < imageGallery.value.length - 1)

/** 面板引用，用于焦点管理 */
const panelRef = ref<HTMLElement | null>(null)

/** 关闭按钮引用 */
const closeBtnRef = ref<HTMLElement | null>(null)

/** 触发元素引用，用于关闭后恢复焦点 */
const triggerElement = ref<HTMLElement | null>(null)

/**
 * 关闭预览
 */
function close() {
  isOpen.value = false
}

/**
 * 切换到上一张图片
 */
function goPrev() {
  if (!hasPrev.value || !imageGallery.value.length) return
  const prev = imageGallery.value[imageIndex.value - 1]
  if (prev) {
    emit('update:attachment', prev)
  }
}

/**
 * 切换到下一张图片
 */
function goNext() {
  if (!hasNext.value || !imageGallery.value.length) return
  const next = imageGallery.value[imageIndex.value + 1]
  if (next) {
    emit('update:attachment', next)
  }
}

/**
 * 键盘事件处理
 */
function onKeydown(e: KeyboardEvent) {
  if (!isOpen.value) return

  if (e.key === 'Escape') {
    e.preventDefault()
    close()
    return
  }

  if (e.key === 'ArrowLeft' && hasPrev.value) {
    e.preventDefault()
    goPrev()
    return
  }

  if (e.key === 'ArrowRight' && hasNext.value) {
    e.preventDefault()
    goNext()
    return
  }

  // 焦点陷阱：Tab 键循环
  if (e.key === 'Tab' && panelRef.value) {
    const focusable = panelRef.value.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    )
    if (focusable.length === 0) return

    const first = focusable[0]
    const last = focusable[focusable.length - 1]

    if (e.shiftKey) {
      if (document.activeElement === first) {
        e.preventDefault()
        last.focus()
      }
    } else {
      if (document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    }
  }
}

/**
 * 打开时焦点进入面板，记录触发元素
 */
watch(isOpen, (val) => {
  if (val) {
    // 记录当前焦点元素，用于关闭后恢复
    triggerElement.value = document.activeElement as HTMLElement
    // 延迟一帧，确保 DOM 更新后聚焦
    requestAnimationFrame(() => {
      closeBtnRef.value?.focus()
    })
  } else {
    // 关闭后恢复焦点
    if (triggerElement.value) {
      triggerElement.value.focus()
      triggerElement.value = null
    }
  }
})

/**
 * 点击遮罩关闭
 */
function onMaskClick(e: MouseEvent) {
  // 只在点击遮罩本身时关闭，不阻止面板内的点击
  if (e.target === e.currentTarget) {
    close()
  }
}

/**
 * 阻止面板内的点击冒泡到遮罩
 */
function onPanelClick(e: MouseEvent) {
  e.stopPropagation()
}

// 按打开态挂卸键盘事件，避免每条消息常驻监听
watch(isOpen, (val) => {
  if (val) {
    document.addEventListener('keydown', onKeydown)
  } else {
    document.removeEventListener('keydown', onKeydown)
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <teleport to="body">
    <div
      v-if="isOpen && attachment"
      class="preview-mask"
      role="dialog"
      aria-modal="true"
      :aria-label="`预览 ${attachment.filename}`"
      @click="onMaskClick"
    >
      <div
        ref="panelRef"
        class="preview-panel"
        :class="{
          'preview-image': isCurrentImage,
          'preview-pdf': isCurrentPdf,
          'preview-degraded': !isCurrentImage && !isCurrentPdf,
        }"
        @click="onPanelClick"
      >
        <header class="preview-head">
          <strong class="preview-title">{{ attachment.filename }}</strong>
          <div class="preview-actions">
            <a
              :href="attachmentContentUrl(attachment)"
              target="_blank"
              rel="noopener"
              class="preview-action-btn"
            >
              {{ isCurrentPdf ? '新窗口打开' : isCurrentImage ? '打开原图' : '打开/下载' }}
            </a>
            <button
              ref="closeBtnRef"
              type="button"
              class="preview-action-btn"
              aria-label="关闭预览"
              @click="close"
            >
              关闭
            </button>
          </div>
        </header>

        <!-- 图片预览 -->
        <div v-if="isCurrentImage" class="preview-body preview-body-image">
          <button
            v-if="hasPrev"
            type="button"
            class="gallery-btn gallery-prev"
            aria-label="上一张图片"
            @click="goPrev"
          >
            ‹
          </button>
          <img
            :src="attachmentContentUrl(attachment)"
            :alt="attachment.filename"
            class="preview-img"
          />
          <button
            v-if="hasNext"
            type="button"
            class="gallery-btn gallery-next"
            aria-label="下一张图片"
            @click="goNext"
          >
            ›
          </button>
          <div v-if="imageGallery.length > 1" class="gallery-counter">
            {{ imageIndex + 1 }} / {{ imageGallery.length }}
          </div>
        </div>

        <!-- PDF 预览 -->
        <div v-else-if="isCurrentPdf" class="preview-body preview-body-pdf">
          <iframe
            :src="attachmentContentUrl(attachment)"
            :title="attachment.filename"
            class="pdf-frame"
          />
        </div>

        <!-- 降级面板：Office 和其他不支持预览的格式 -->
        <div v-else class="preview-body preview-body-degraded">
          <div class="degraded-content">
            <div class="degraded-icon">
              <svg viewBox="0 0 24 24" width="48" height="48" aria-hidden="true">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
                <path d="M14 2v6h6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
              </svg>
            </div>
            <div class="degraded-info">
              <span class="degraded-badge">{{ fileKind(attachment) }}</span>
              <strong class="degraded-filename">{{ attachment.filename }}</strong>
              <p v-if="attachment.size" class="degraded-size">{{ formatSize(attachment.size) }}</p>
              <p class="degraded-hint">此格式暂不支持应用内预览</p>
            </div>
            <div class="degraded-actions">
              <a
                :href="attachmentContentUrl(attachment)"
                target="_blank"
                rel="noopener"
                class="degraded-action-btn primary"
              >
                打开/下载
              </a>
              <button
                type="button"
                class="degraded-action-btn"
                @click="close"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.preview-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(16, 24, 32, .55);
  animation: mask-in .2s ease;
}

@keyframes mask-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.preview-panel {
  width: min(920px, 100%);
  max-height: 90vh;
  overflow: auto;
  border-radius: 12px;
  background: #111;
  box-shadow: 0 20px 48px rgba(0,0,0,.35);
  animation: panel-in .25s ease;
}

@keyframes panel-in {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.preview-panel.preview-pdf {
  width: min(1040px, 100%);
  height: min(90vh, 860px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.preview-panel.preview-degraded {
  width: min(480px, 100%);
  max-height: 80vh;
  overflow: visible;
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  color: #fff;
  background: #1b232b;
  border-radius: 12px 12px 0 0;
}

.preview-title {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.preview-actions {
  display: flex;
  gap: 8px;
  flex: none;
}

.preview-action-btn {
  border: 0;
  border-radius: 6px;
  padding: 10px 14px;
  min-height: 44px;
  min-width: 44px;
  background: rgba(255,255,255,.1);
  color: #fff;
  font-size: 12px;
  text-decoration: none;
  cursor: pointer;
  transition: background .15s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.preview-action-btn:hover {
  background: rgba(255,255,255,.2);
}

.preview-body {
  position: relative;
}

.preview-body-image {
  display: grid;
  place-items: center;
  min-height: 200px;
  max-height: calc(90vh - 52px);
  overflow: hidden;
  background: #000;
}

.preview-img {
  display: block;
  width: 100%;
  max-height: calc(90vh - 52px);
  object-fit: contain;
  background: #000;
}

.gallery-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  background: rgba(0,0,0,.5);
  color: #fff;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: background .15s ease;
  z-index: 10;
}

.gallery-btn:hover {
  background: rgba(0,0,0,.8);
}

.gallery-prev {
  left: 12px;
}

.gallery-next {
  right: 12px;
}

.gallery-counter {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 12px;
  background: rgba(0,0,0,.6);
  color: #fff;
  font-size: 12px;
  border-radius: 999px;
}

.preview-body-pdf {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.pdf-frame {
  flex: 1;
  width: 100%;
  border: 0;
  background: #fff;
}

.preview-body-degraded {
  padding: 24px;
  background: #1b232b;
  border-radius: 0 0 12px 12px;
}

.degraded-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  text-align: center;
}

.degraded-icon {
  color: rgba(255,255,255,.4);
}

.degraded-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.degraded-badge {
  display: inline-block;
  min-width: 40px;
  height: 24px;
  padding: 0 8px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: rgba(255,255,255,.15);
  color: rgba(255,255,255,.8);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.degraded-filename {
  color: #fff;
  font-size: 14px;
  word-break: break-all;
}

.degraded-size {
  margin: 0;
  color: rgba(255,255,255,.5);
  font-size: 12px;
}

.degraded-hint {
  margin: 4px 0 0;
  color: rgba(255,255,255,.6);
  font-size: 12px;
}

.degraded-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.degraded-action-btn {
  border: 0;
  border-radius: 6px;
  padding: 8px 16px;
  background: rgba(255,255,255,.1);
  color: #fff;
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
  transition: background .15s ease;
}

.degraded-action-btn:hover {
  background: rgba(255,255,255,.2);
}

.degraded-action-btn.primary {
  background: var(--c-primary, #0d6b67);
}

.degraded-action-btn.primary:hover {
  background: color-mix(in srgb, var(--c-primary, #0d6b67) 90%, #000);
}

@media (prefers-reduced-motion: reduce) {
  .preview-mask,
  .preview-panel {
    animation: none;
  }
}

@media (max-width: 768px) {
  .preview-mask {
    padding: 16px;
  }

  .preview-panel {
    width: 100%;
    max-height: 95vh;
    border-radius: 8px;
  }

  .preview-head {
    border-radius: 8px 8px 0 0;
  }

  .preview-body-degraded {
    border-radius: 0 0 8px 8px;
  }

  .gallery-btn {
    width: 36px;
    height: 36px;
    font-size: 20px;
  }

  .gallery-prev {
    left: 8px;
  }

  .gallery-next {
    right: 8px;
  }
}
</style>
