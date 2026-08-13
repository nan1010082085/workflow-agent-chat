<script setup lang="ts">
/**
 * 应用标记：与 favicon.ico 同构——实心圆角方 + 居中白色字形。
 * - product：青绿品牌标（W）
 * - ai：暖橙助手标（星形或自定义 icon）
 */
const props = withDefaults(
  defineProps<{
    variant?: 'product' | 'ai'
    size?: 'sm' | 'md' | 'lg'
    /**
     * 自定义字形；空 / ✦ / ★ 时 AI 变体使用默认星形 SVG。
     */
    glyph?: string
  }>(),
  {
    variant: 'product',
    size: 'md',
    glyph: '',
  },
)

/**
 * 是否渲染自定义字形（否则走变体默认标）。
 */
function hasCustomGlyph(value: string): boolean {
  return Boolean(value) && value !== '✦' && value !== '★'
}
</script>

<template>
  <span class="app-mark" :class="[`is-${variant}`, `is-${size}`]" aria-hidden="true">
    <template v-if="hasCustomGlyph(props.glyph)">{{ props.glyph }}</template>
    <template v-else-if="variant === 'product'">W</template>
    <svg v-else viewBox="0 0 16 16" class="star" focusable="false">
      <path
        d="M8 1.8 9.6 5.4l3.8.4-2.9 2.6.9 3.7L8 10.4l-3.4 1.7.9-3.7L2.6 5.8l3.8-.4L8 1.8Z"
        fill="currentColor"
      />
    </svg>
  </span>
</template>

<style scoped>
.app-mark {
  display: grid;
  place-items: center;
  flex: none;
  color: #fff;
  border-radius: var(--radius-lg);
  line-height: 1;
  font-weight: 800;
  user-select: none;
}
.app-mark.is-product {
  background: var(--c-primary);
}
.app-mark.is-ai {
  background: var(--c-accent);
}
.app-mark.is-sm {
  width: 28px;
  height: 28px;
  font-size: 13px;
}
.app-mark.is-md {
  width: 34px;
  height: 34px;
  font-size: 15px;
}
.app-mark.is-lg {
  width: 56px;
  height: 56px;
  font-size: 24px;
  border-radius: 14px;
}
.star {
  display: block;
  width: 52%;
  height: 52%;
}
</style>
