<template>
  <v-container class="py-8" style="max-width: 980px;">
    <v-card elevation="2" rounded="lg" class="pa-6 pa-md-8">
      <div class="d-flex align-center mb-2">
        <v-icon color="primary" class="mr-2">mdi-file-document-check</v-icon>
        <h1 class="text-h4 font-weight-bold">Terms of Service</h1>
      </div>
      <p class="text-body-2 text-medium-emphasis mb-1">AI Marketing Platform (adverion.xyz) by Adverion</p>
      <p class="text-body-2 text-medium-emphasis mb-4">Last updated: March 2026</p>

      <v-alert type="info" variant="tonal" class="mb-6">
        Primary language: English. Bulgarian version is available on request at
        <a href="mailto:support@adverion.xyz">support@adverion.xyz</a>.
      </v-alert>

      <h2 class="text-h6 font-weight-bold mb-2">1. Acceptance of terms</h2>
      <p class="mb-4">By accessing or using AI Marketing Platform, you agree to these Terms of Service.</p>

      <h2 class="text-h6 font-weight-bold mb-2">2. Description of service</h2>
      <p class="mb-4">
        AI Marketing Platform is a SaaS solution for agencies to manage Meta advertising operations,
        analyze performance, generate reports, and collaborate on optimization.
      </p>

      <h2 class="text-h6 font-weight-bold mb-2">3. User accounts and responsibilities</h2>
      <ul class="mb-4 ml-5">
        <li>You must keep account credentials secure and confidential.</li>
        <li>You are responsible for all activity performed through your account.</li>
        <li>You must provide accurate information and use the service lawfully.</li>
      </ul>

      <h2 class="text-h6 font-weight-bold mb-2">4. Agency responsibilities</h2>
      <ul class="mb-4 ml-5">
        <li>Agencies are responsible for the legality and accuracy of client and campaign data they provide.</li>
        <li>Agencies must comply with Meta/Facebook platform terms and ad policies.</li>
        <li>Agencies must have lawful authority to process data on behalf of their clients.</li>
      </ul>

      <h2 class="text-h6 font-weight-bold mb-2">5. AI features disclaimer</h2>
      <p class="mb-4">
        AI suggestions and summaries are recommendations only. They do not constitute legal, financial,
        or guaranteed performance advice. Final decisions remain with the user.
      </p>

      <h2 class="text-h6 font-weight-bold mb-2">6. Intellectual property</h2>
      <ul class="mb-4 ml-5">
        <li>You retain ownership of your uploaded content and marketing materials.</li>
        <li>Adverion retains ownership of the platform, software, and related intellectual property.</li>
        <li>You grant Adverion a limited license to process your data solely to deliver the service.</li>
      </ul>

      <h2 class="text-h6 font-weight-bold mb-2">7. Payment terms</h2>
      <p class="mb-4">Fees and billing terms apply as per your subscription agreement.</p>

      <h2 class="text-h6 font-weight-bold mb-2">8. Data and privacy</h2>
      <p class="mb-4">
        Data handling is governed by our Privacy Policy available at
        <router-link to="/privacy">/privacy</router-link>.
      </p>

      <h2 class="text-h6 font-weight-bold mb-2">9. Limitation of liability</h2>
      <p class="mb-4">
        To the maximum extent permitted by law, Adverion is not liable for indirect, incidental,
        consequential, or punitive damages, including lost profits or business interruption.
      </p>

      <h2 class="text-h6 font-weight-bold mb-2">10. Termination</h2>
      <p class="mb-4">
        We may suspend or terminate access for material breach, unlawful use, or security risk.
        You may discontinue use subject to contractual obligations.
      </p>

      <h2 class="text-h6 font-weight-bold mb-2">11. Governing law</h2>
      <p class="mb-4">These Terms are governed by the laws of Bulgaria, unless mandatory law states otherwise.</p>

      <h2 class="text-h6 font-weight-bold mb-2">12. Changes to terms</h2>
      <p class="mb-4">We may revise these Terms periodically. Continued use indicates acceptance of updates.</p>

      <h2 class="text-h6 font-weight-bold mb-2">13. Contact</h2>
      <p>
        For legal or support inquiries, contact
        <a href="mailto:support@adverion.xyz">support@adverion.xyz</a>.
      </p>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
</script>
