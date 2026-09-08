<script setup>defineProps({ visible: Boolean }); const emit = defineEmits(['open'])</script>
<template><button v-if="visible" class="leaf-guide" aria-label="打开乌东向导" @click="emit('open')"><span aria-hidden="true">◒</span><small>一叶同行</small></button></template>
