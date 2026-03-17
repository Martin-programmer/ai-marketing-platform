<template>
  <v-card variant="outlined" class="ad-preview-card h-100">
    <div class="ad-preview-image-wrap">
      <img v-if="imageUrl" :src="imageUrl" class="ad-preview-image" />
      <div v-else class="ad-preview-image-placeholder">
        <v-icon size="42" color="grey">mdi-image-off-outline</v-icon>
      </div>
    </div>
    <v-card-text>
      <div class="text-subtitle-2 font-weight-medium mb-2">{{ headline || 'Headline preview' }}</div>
      <div class="text-body-2 mb-2 preview-copy">{{ primaryText || 'Primary text preview' }}</div>
      <div class="text-caption text-medium-emphasis mb-4 preview-copy">{{ description || 'Description preview' }}</div>
      <div class="d-flex align-center flex-wrap ga-2 mb-2">
        <v-btn size="small" color="primary" variant="flat" disabled>{{ cta || 'LEARN_MORE' }}</v-btn>
      </div>
      <div class="text-caption text-medium-emphasis text-truncate">{{ destinationUrl || 'destination-url.com' }}</div>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
defineProps<{
  imageUrl?: string | null
  headline?: string | null
  primaryText?: string | null
  description?: string | null
  cta?: string | null
  destinationUrl?: string | null
}>()
</script>

<style scoped>
.ad-preview-card {
  overflow: hidden;
}

.ad-preview-image-wrap {
  height: 220px;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ad-preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ad-preview-image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.preview-copy {
  white-space: pre-wrap;
}
</style>
