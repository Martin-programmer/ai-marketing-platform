# AI Marketing Platform — Developer Onboarding Guide

## Какво е проекта

SaaS платформа за маркетинг агенции — свързва Meta (Facebook/Instagram) рекламни акаунти, синхронизира данни, показва KPI-та, генерира отчети и AI предложения. Всяка агенция управлява своите клиенти, а клиентите имат read-only портал.

---

## 1. Архитектура

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│  Frontend   │────▶│   Backend    │────▶│  PostgreSQL   │
│  Vue 3 +    │     │  Spring Boot │     │     16        │
│  Vuetify    │     │  Java 21     │     └──────────────┘
│  TypeScript │     │  Port 8080   │────▶┌──────────────┐
│  Port 5173  │     └──────────────┘     │   Redis 7    │
└─────────────┘                          └──────────────┘
```

**Monorepo структура:**
```
ai-marketing-platform/
├── backend/              ← Spring Boot API
│   ├── src/main/java/com/amp/
│   │   ├── agency/       ← Agency CRUD
│   │   ├── auth/         ← Users, JWT, RBAC, Permissions
│   │   ├── clients/      ← Clients, profiles, permissions
│   │   ├── campaigns/    ← Campaigns, adsets, ads
│   │   ├── creatives/    ← Creative assets, packages
│   │   ├── insights/     ← InsightDaily, KPI aggregations
│   │   ├── ai/           ← AI suggestions, action log
│   │   ├── reports/      ← Reports, feedback
│   │   ├── meta/         ← Meta OAuth, sync, Graph API client
│   │   ├── audit/        ← Audit log
│   │   ├── tenancy/      ← Multi-tenant context (ThreadLocal)
│   │   ├── common/       ← RoleGuard, exceptions, OpenAPI config
│   │   └── ops/          ← Redis health, correlation IDs
│   ├── src/main/resources/
│   │   ├── application.yml              ← Обща конфигурация
│   │   ├── application-local.yml        ← Локална среда
│   │   ├── application-staging.yml      ← Staging среда
│   │   └── db/migration/
│   │       ├── V001__init.sql           ← Users, agencies, clients
│   │       ├── V002__meta.sql           ← Meta connections, sync jobs
│   │       ├── V003__creatives.sql      ← Creative assets, packages
│   │       ├── V004__campaigns.sql      ← Campaigns, insights
│   │       ├── V005__ai_reports.sql     ← AI suggestions, reports
│   │       ├── V006__portal.sql         ← Client portal
│   │       └── V007__permissions_refactor.sql ← Granular RBAC
│   └── pom.xml
│
├── frontend/             ← Vue 3 + Vuetify + TypeScript
│   ├── src/
│   │   ├── api/          ← Axios HTTP клиент и API helpers
│   │   ├── views/        ← Страниците (по една за всеки route)
│   │   ├── components/   ← Reusable компоненти
│   │   ├── stores/       ← Pinia state management
│   │   ├── router/       ← Vue Router конфигурация
│   │   └── App.vue       ← Root компонент с навигация
│   ├── nginx.conf        ← Nginx конфигурация (production)
│   ├── Dockerfile
│   └── package.json
│
├── scripts/
│   └── db-seed.sql       ← Seed данни за ЛОКАЛНА среда
│
├── docker-compose.yml    ← Локални services (Postgres, Redis, Mailpit)
└── README.md
```

---

## 2. Роли и права (RBAC)

```
OWNER_ADMIN (agency_id = NULL)
│   Суперадмин на платформата. Вижда всичко, управлява агенции.
│
└── Agency (tenant)
     ├── AGENCY_ADMIN
     │   Собственик на агенцията. Пълен достъп до всички клиенти.
     │   Управлява екипа, раздава права.
     │
     ├── AGENCY_USER
     │   Служител. Вижда САМО назначените му клиенти.
     │   Има САМО правата, които му е дал AGENCY_ADMIN.
     │
     └── CLIENT_USER
         Клиент. Read-only портал за своя клиент.
```

**Granular permissions (за AGENCY_USER per client):**

| Permission | Описание |
|---|---|
| CLIENT_VIEW | Вижда клиентски данни |
| CLIENT_EDIT | Редактира профил |
| CAMPAIGNS_VIEW | Вижда кампании |
| CAMPAIGNS_EDIT | Създава/редактира кампании |
| CAMPAIGNS_PUBLISH | Публикува към Meta |
| CREATIVES_VIEW | Вижда creative library |
| CREATIVES_EDIT | Качва/управлява creatives |
| REPORTS_VIEW | Вижда отчети |
| REPORTS_EDIT | Създава/редактира отчети |
| REPORTS_SEND | Изпраща отчети |
| META_MANAGE | Управлява Meta интеграция |
| AI_VIEW | Вижда AI предложения |
| AI_APPROVE | Одобрява AI предложения |

**Presets:** READ_ONLY, EDITOR, FULL_ACCESS

---

## 3. Как се работи локално

### 3.1 Първоначална настройка

```bash
# Клонирай репото
git clone https://github.com/Martin-programmer/ai-marketing-platform.git
cd ai-marketing-platform

# Стартирай инфраструктурата (PostgreSQL 16, Redis 7, Mailpit)
docker compose up -d

# Стартирай backend-а
cd backend
./mvnw spring-boot:run
# API слуша на http://localhost:8080

# В нов терминал — стартирай frontend-а
cd frontend
npm install
npm run dev
# UI слуша на http://localhost:5173
```

### 3.2 Seed данни (локална среда)

```bash
# Зареди тестови данни
./scripts/db-seed.sh
```

**Тестови акаунти (локално):**

| Email | Парола | Роля |
|---|---|---|
| owner_admin@local | admin123 | OWNER_ADMIN |
| agency_admin@local | admin123 | AGENCY_ADMIN |
| agency_user@local | admin123 | AGENCY_USER |

### 3.3 Полезни URL-и (локално)

| Какво | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Mailpit (test email) | http://localhost:8025 |

---

## 4. База данни — как се правят промени

### 4.1 Flyway миграции

Всяка промяна на DB схемата се прави чрез Flyway миграция. Миграциите са SQL файлове в:

```
backend/src/main/resources/db/migration/
```

**Правила за именуване:**
```
V{номер}__{описание}.sql
```

Примери:
```
V008__add_client_logo.sql
V009__extend_campaign_fields.sql
V010__add_report_templates.sql
```

**ВАЖНО:**
- Номерът ТРЯБВА да е уникален и следващ по ред (след V007 → V008)
- Двойно underscore `__` между номер и описание
- НИКОГА не редактирай стара миграция — винаги добавяй нова
- Миграциите се изпълняват автоматично при стартиране на backend-а

### 4.2 Пример: Добавяне на ново поле

Искаш да добавиш `logo_url` към таблица `client`.

**Стъпка 1:** Създай миграция:

```sql
-- V008__add_client_logo.sql
ALTER TABLE client ADD COLUMN logo_url text NULL;
```

**Стъпка 2:** Обнови Java entity-то:

В `backend/src/main/java/com/amp/clients/Client.java` добави:

```java
@Column(name = "logo_url")
private String logoUrl;

// getter и setter
public String getLogoUrl() { return logoUrl; }
public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
```

**Стъпка 3:** Обнови Response DTO-то:

В `ClientResponse.java` добави полето и го маппни от entity-то.

**Стъпка 4:** Ако трябва да се записва — обнови Request DTO-то.

**Стъпка 5:** Рестартирай backend-а. Flyway ще изпълни миграцията автоматично.

### 4.3 Пример: Нова таблица

```sql
-- V009__client_settings.sql

CREATE TABLE client_setting (
    id          uuid            NOT NULL DEFAULT gen_random_uuid(),
    client_id   uuid            NOT NULL,
    setting_key text            NOT NULL,
    setting_val text            NULL,
    created_at  timestamptz     NOT NULL DEFAULT now(),
    updated_at  timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT pk_client_setting PRIMARY KEY (id),
    CONSTRAINT fk_client_setting_client FOREIGN KEY (client_id)
        REFERENCES client(id) ON DELETE CASCADE,
    CONSTRAINT uq_client_setting UNIQUE (client_id, setting_key)
);

CREATE INDEX idx_client_setting_client ON client_setting(client_id);
```

След това: Entity → Repository → Service → Controller → Frontend.

### 4.4 Seed данни (само локална среда)

Файлът `scripts/db-seed.sql` съдържа тестови данни САМО за локално развитие. Staging-ът работи с реални данни.

Ако добавяш нова таблица — добави и тестови данни в seed файла:

```sql
-- В scripts/db-seed.sql
INSERT INTO client_setting (id, client_id, setting_key, setting_val, created_at, updated_at)
VALUES ('...uuid...', '...client_uuid...', 'notification_email', 'test@example.com', now(), now())
ON CONFLICT DO NOTHING;
```

---

## 5. Backend — как е структуриран

### 5.1 Стандартна структура на модул

Всеки модул следва pattern-а:

```
com.amp.{module}/
├── {Entity}.java              ← JPA Entity (маппинг към таблица)
├── {Entity}Repository.java    ← Spring Data JPA Repository
├── {Entity}Service.java       ← Бизнес логика
├── {Entity}Controller.java    ← REST endpoints
├── {Entity}Response.java      ← Response DTO (какво API-то връща)
├── {Request}Request.java      ← Request DTO (какво API-то приема)
└── ...
```

### 5.2 Пример: Добавяне на нов endpoint

Ако искаш да добавиш GET `/api/v1/clients/{clientId}/settings`:

```java
// В ClientController.java или нов SettingsController
@GetMapping("/{clientId}/settings")
public ResponseEntity<?> getSettings(@PathVariable UUID clientId) {
    accessControl.requireClientPermission(clientId, Permission.CLIENT_VIEW);
    // ... логика
    return ResponseEntity.ok(settings);
}
```

**ВАЖНО:** Винаги проверявай права с `AccessControl`:
- `accessControl.requireClientPermission(clientId, Permission.XXX)` — за client-scoped операции
- `RoleGuard.requireAgencyAdmin()` — само AGENCY_ADMIN и OWNER_ADMIN
- `RoleGuard.requireOwnerAdmin()` — само OWNER_ADMIN

### 5.3 Тестове

Тестовете са в `backend/src/test/`. Пусни ги с:

```bash
cd backend
./mvnw test
```

Всички тестове ТРЯБВА да минават преди commit. Ако добавиш нова функционалност — добави и тест.

---

## 6. Frontend — как е структуриран

### 6.1 Технологии

- **Vue 3** с Composition API (`<script setup>`)
- **TypeScript**
- **Vuetify** за UI компоненти (Material Design)
- **Pinia** за state management
- **Vue Router** за навигация
- **Axios** за HTTP заявки

### 6.2 Структура

```
frontend/src/
├── api/
│   ├── client.ts       ← Axios instance (baseURL, interceptors)
│   ├── owner.ts        ← Owner admin API calls
│   └── permissions.ts  ← Permissions API calls
├── views/
│   ├── LoginView.vue
│   ├── DashboardView.vue
│   ├── ClientsView.vue
│   ├── CampaignsView.vue
│   ├── MetaView.vue
│   ├── owner/
│   │   ├── OwnerDashboardView.vue
│   │   └── OwnerAgencyDetailView.vue
│   └── portal/
│       └── PortalDashboardView.vue
├── stores/
│   ├── auth.ts         ← Login/logout, JWT token
│   ├── clients.ts      ← Client data
│   ├── meta.ts         ← Meta connection state
│   └── ...
├── router/
│   └── index.ts        ← Routes + navigation guards
└── App.vue             ← Layout + navigation sidebar
```

### 6.3 Как да добавиш нова страница

**Стъпка 1:** Създай view:

```vue
<!-- frontend/src/views/MyNewView.vue -->
<template>
  <div>
    <h1 class="mb-4">My New Page</h1>
    
    <v-card>
      <v-card-title>Content</v-card-title>
      <v-card-text>
        <!-- Vuetify компоненти -->
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '@/api/client'

const data = ref<any[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await api.get('/my-endpoint')
    data.value = res.data
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})
</script>
```

**Стъпка 2:** Добави route:

```typescript
// В frontend/src/router/index.ts
{
  path: '/my-page',
  name: 'my-page',
  component: () => import('@/views/MyNewView.vue'),
  meta: { requiredRole: ['AGENCY_ADMIN', 'AGENCY_USER'] }
}
```

**Стъпка 3:** Добави в навигацията:

В `App.vue` добави навигационен елемент в съответния масив.

### 6.4 Vuetify компоненти (най-използвани)

| Компонент | За какво |
|---|---|
| `v-card` | Карта/контейнер |
| `v-data-table` | Таблица с данни |
| `v-btn` | Бутон |
| `v-dialog` | Модален прозорец |
| `v-text-field` | Текстово поле |
| `v-textarea` | Голям текст |
| `v-select` | Dropdown |
| `v-chip` | Тагове/статуси |
| `v-alert` | Съобщения (info/warning/error) |
| `v-progress-linear` | Loading bar |
| `v-snackbar` | Toast notification |
| `v-row` / `v-col` | Grid layout |

Документация: https://vuetifyjs.com/en/components/all/

### 6.5 API заявки

Всички заявки минават през `api/client.ts`. Не ползвай `fetch()` директно.

```typescript
import api from '@/api/client'

// GET
const res = await api.get('/clients')

// POST
const res = await api.post('/clients', { name: 'New Client', industry: 'ECOM' })

// PATCH
const res = await api.patch(`/clients/${id}`, { name: 'Updated Name' })

// DELETE
await api.delete(`/clients/${id}`)
```

API-то автоматично добавя JWT token и обработва 401 (redirect to login).

---

## 7. Git workflow

### 7.1 Основни правила

- **main** е единственият branch (за сега)
- Commit-вай често с ясни съобщения
- Всички тестове ТРЯБВА да минават преди push
- НИКОГА не commit-вай secret-и, пароли, API ключове, .tar файлове

### 7.2 Как да commit-неш

```bash
# Провери статуса
git status

# Добави промените
git add -A

# Commit с ясно съобщение
git commit -m "feat: add logo_url field to client profile"

# Push
git push origin main
```

### 7.3 Commit message конвенция

```
feat: добави нова функционалност
fix: оправи бъг
refactor: преструктуриране без нова функционалност
style: UI/CSS промени
docs: документация
test: тестове
chore: build, config и т.н.
```

### 7.4 .gitignore (вече конфигуриран)

Тези файлове НЕ се commit-ват:
- `*.tar`, `*.tar.gz` (Docker images)
- `policy.json` (AWS)
- `node_modules/`, `target/`
- `.env` файлове с реални secret-и

---

## 8. Среди

### 8.1 Локална среда (за разработка)

| Компонент | Адрес |
|---|---|
| Frontend | http://localhost:5173 |
| Backend | http://localhost:8080 |
| PostgreSQL | localhost:5432 (user: amp, pass: amp, db: amp) |
| Redis | localhost:6379 |
| Swagger | http://localhost:8080/swagger-ui.html |

```bash
# Стартирай инфраструктурата
docker compose up -d

# Стартирай backend
cd backend && ./mvnw spring-boot:run

# Стартирай frontend (друг терминал)
cd frontend && npm run dev
```

### 8.2 Staging среда (deploy прави Мартин)

| Компонент | Адрес |
|---|---|
| Платформа | https://adverion.xyz |
| Backend API | https://adverion.xyz/api/v1/* |
| PostgreSQL | AWS RDS (не е публично достъпна) |

**Ти нямаш достъп до staging сървъра.** Правиш промени локално, commit-ваш и push-ваш. Мартин прави deploy когато реши.

### 8.3 Какво се случва при deploy

Мартин изпълнява:
1. `mvnw package` — build-ва backend
2. `npm run build-only` — build-ва frontend
3. `docker build` — създава Docker images
4. Upload-ва images на EC2
5. Рестартира контейнерите
6. Flyway миграциите се изпълняват автоматично при старт на backend-а

**Затова е критично:**
- Миграциите ти да са правилни и backwards-compatible
- Тестовете да минават
- Да не чупиш съществуващи endpoint-и

---

## 9. Практически примери за твоите задачи

### 9.1 Добавяне на ново поле в Client Profile

1. **Миграция:** `V008__add_client_fields.sql`
   ```sql
   ALTER TABLE client ADD COLUMN website_url text NULL;
   ALTER TABLE client ADD COLUMN phone text NULL;
   ALTER TABLE client ADD COLUMN address text NULL;
   ```

2. **Entity:** Обнови `Client.java` — добави полетата + getters/setters

3. **Response DTO:** Обнови `ClientResponse.java` — добави полетата в record-а

4. **Request DTO:** Обнови `UpdateClientRequest.java` — добави полетата

5. **Frontend:** Обнови `ClientsView.vue` или `ClientDetailView.vue` — добави `v-text-field` за новите полета

6. **Seed:** Обнови `scripts/db-seed.sql` — добави примерни стойности

### 9.2 Подобряване на UI/UX

Свободен си да променяш всичко в `frontend/src/views/` и `frontend/src/components/`. Основни насоки:

- Ползвай Vuetify компоненти (не custom CSS)
- Loading states: `v-progress-linear` или `v-skeleton-loader`
- Empty states: `v-alert type="info"` когато няма данни
- Error states: `v-alert type="error"` при грешки
- Responsive: ползвай `v-row` / `v-col` с breakpoints

### 9.3 Нов екран с форма

Пример — екран за редакция на клиентски профил:

```vue
<template>
  <div>
    <h1 class="mb-4">Client Profile</h1>

    <v-progress-linear v-if="loading" indeterminate />

    <v-card v-if="!loading && profile">
      <v-card-text>
        <v-text-field v-model="profile.websiteUrl" label="Website" variant="outlined" class="mb-2" />
        <v-text-field v-model="profile.phone" label="Phone" variant="outlined" class="mb-2" />
        <v-textarea v-model="profile.description" label="Description" variant="outlined" rows="4" />
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn color="primary" @click="save" :loading="saving">Save</v-btn>
      </v-card-actions>
    </v-card>

    <v-snackbar v-model="snackbar.show" :color="snackbar.color" timeout="3000">
      {{ snackbar.text }}
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '@/api/client'

const props = defineProps<{ clientId: string }>()

const loading = ref(false)
const saving = ref(false)
const profile = ref<any>(null)
const snackbar = ref({ show: false, text: '', color: 'success' })

onMounted(async () => {
  loading.value = true
  try {
    const res = await api.get(`/clients/${props.clientId}/profile`)
    profile.value = res.data
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})

async function save() {
  saving.value = true
  try {
    await api.put(`/clients/${props.clientId}/profile`, profile.value)
    snackbar.value = { show: true, text: 'Saved!', color: 'success' }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || 'Error', color: 'error' }
  } finally {
    saving.value = false
  }
}
</script>
```

---

## 10. Чести грешки и как да ги избегнеш

| Грешка | Решение |
|---|---|
| Редактирах стара миграция | НИКОГА не го прави. Добави нова миграция. |
| Забравих `ON CONFLICT DO NOTHING` в seed | Seed-ът трябва да е idempotent. Добави го. |
| Не минават тестове | Не push-вай. Оправи ги първо. |
| Счупих съществуващ endpoint | Не мени URL-и/response формат без съгласуване. |
| Commit-нах secret | Уведоми Мартин незабавно. |
| `application-local.yml` не тръгва | Провери дали `docker compose up -d` е пуснат. |
| Frontend не вижда API | Backend слуша на 8080, frontend proxy-ва `/api` → 8080. |

---

## 11. Контакти и процес

- **Git repo:** https://github.com/Martin-programmer/ai-marketing-platform.git
- **Branch:** main
- **Deploy:** Мартин решава кога
- **Staging URL:** https://adverion.xyz
- **Въпроси:** Питай Мартин преди да правиш breaking changes

---

## 12. Бърз checklist преди push

- [ ] `cd backend && ./mvnw test` — всички тестове минават
- [ ] Новата миграция е с правилен номер (V008, V009...)
- [ ] Entity, Response DTO, Request DTO са обновени
- [ ] Frontend компилира без грешки: `cd frontend && npm run build-only`
- [ ] Seed данни обновени (ако е нужно)
- [ ] Commit message е ясно: `feat: ...` / `fix: ...`
- [ ] Няма secret-и в кода
