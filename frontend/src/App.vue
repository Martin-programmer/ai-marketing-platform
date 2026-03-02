<template>
  <v-app>
    <v-navigation-drawer v-model="drawer" app>
      <v-list density="compact" nav>
        <v-list-item prepend-icon="mdi-view-dashboard" title="Dashboard" to="/" />
        <v-list-item prepend-icon="mdi-account-group" title="Clients" to="/clients" />
        <v-list-item prepend-icon="mdi-image-multiple" title="Creatives" to="/creatives" />
        <v-list-item prepend-icon="mdi-bullhorn" title="Campaigns" to="/campaigns" />
        <v-list-item prepend-icon="mdi-lightbulb-on" title="AI Suggestions" to="/suggestions" />
        <v-list-item prepend-icon="mdi-file-chart" title="Reports" to="/reports" />
        <v-list-item prepend-icon="mdi-facebook" title="Meta" to="/meta" />
      </v-list>
    </v-navigation-drawer>

    <v-app-bar app color="primary" dark>
      <v-app-bar-nav-icon @click="drawer = !drawer" />
      <v-toolbar-title>AI Marketing Platform</v-toolbar-title>
    </v-app-bar>

    <v-main>
      <v-container fluid>
        <router-view />
      </v-container>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import { ref } from 'vue'
const drawer = ref(true)
</script>
