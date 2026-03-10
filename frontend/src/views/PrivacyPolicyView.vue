<template>
  <v-container class="py-8" style="max-width: 980px;">
    <v-card elevation="2" rounded="lg" class="pa-6 pa-md-8">
      <div class="d-flex align-center mb-2">
        <v-icon color="primary" class="mr-2">mdi-shield-account</v-icon>
        <h1 class="text-h4 font-weight-bold">Privacy Policy</h1>
      </div>
      <p class="text-body-2 text-medium-emphasis mb-1">AI Marketing Platform (adverion.xyz) by Adverion</p>
      <p class="text-body-2 text-medium-emphasis mb-4">Last updated: March 2026</p>

      <v-alert type="info" variant="tonal" class="mb-6">
        Primary language: English. Bulgarian version is available on request at
        <a href="mailto:support@adverion.xyz">support@adverion.xyz</a>.
      </v-alert>

      <h2 class="text-h6 font-weight-bold mb-2">1. What data we collect</h2>
      <ul class="mb-4 ml-5">
        <li>Account data: name, email, role, organization relationships, and login metadata.</li>
        <li>Meta ad data through Meta/Facebook API (campaigns, ad sets, ads, insights).</li>
        <li>Creative assets and related marketing content uploaded by users.</li>
        <li>Usage analytics and system logs for security and product operations.</li>
      </ul>

      <h2 class="text-h6 font-weight-bold mb-2">2. How we use data</h2>
      <ul class="mb-4 ml-5">
        <li>To provide campaign management, reporting, and collaboration features.</li>
        <li>To generate AI-assisted analysis, suggestions, and summaries.</li>
        <li>To maintain platform reliability, security, and customer support.</li>
        <li>To meet legal obligations and enforce contractual terms.</li>
      </ul>

      <h2 class="text-h6 font-weight-bold mb-2">3. Third-party services</h2>
      <p class="mb-2">We integrate with:</p>
      <ul class="mb-4 ml-5">
        <li>Meta/Facebook API for advertising data and connection workflows.</li>
        <li>Amazon Web Services (AWS) for infrastructure, storage, and operational services.</li>
        <li>Anthropic Claude AI for AI-powered text processing features.</li>
      </ul>

      <h2 class="text-h6 font-weight-bold mb-2">4. Data storage and security</h2>
      <ul class="mb-4 ml-5">
        <li>Data is hosted on secured AWS infrastructure.</li>
        <li>Encryption is used in transit and, where applicable, at rest.</li>
        <li>Role-based access controls and tenant isolation are enforced.</li>
        <li>Security events and sensitive operations are monitored and logged.</li>
      </ul>

      <h2 class="text-h6 font-weight-bold mb-2">5. Retention and deletion</h2>
      <p class="mb-4">
        We retain data only as long as needed to provide the service, support contractual obligations,
        and comply with applicable law. On valid request or account termination, data is deleted or anonymized,
        unless legal retention duties apply.
      </p>

      <h2 class="text-h6 font-weight-bold mb-2">6. Cookies and session handling</h2>
      <p class="mb-4">
        We use minimal client-side storage required for authentication/session handling (for example,
        JWT-based authentication state). We do not rely on unnecessary advertising cookies for core product usage.
      </p>

      <h2 class="text-h6 font-weight-bold mb-2">7. Your rights (GDPR and similar laws)</h2>
      <p class="mb-4">
        Subject to applicable law, you may have rights to access, correction, deletion, restriction,
        objection, and data portability/export. To submit a rights request, contact
        <a href="mailto:support@adverion.xyz">support@adverion.xyz</a>.
      </p>

      <h2 class="text-h6 font-weight-bold mb-2">8. Children’s privacy</h2>
      <p class="mb-4">The service is not intended for individuals under 18 years of age.</p>

      <h2 class="text-h6 font-weight-bold mb-2">9. Changes to this policy</h2>
      <p class="mb-4">
        We may update this policy from time to time. Material updates will be reflected by an updated date
        and, when required, additional notice.
      </p>

      <h2 class="text-h6 font-weight-bold mb-2">10. Contact</h2>
      <p>
        For privacy-related inquiries, contact
        <a href="mailto:support@adverion.xyz">support@adverion.xyz</a>.
      </p>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
</script>
