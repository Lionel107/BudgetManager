# Edge Functions — Supabase

## `financial-advisor`

Analyse les 12 derniers mois de l'utilisateur, calcule saisonnalité + budget annuel
lissé (déterministe), et génère un accompagnement rédigé via Gemini.
La clé Gemini reste **côté serveur** (secret), jamais exposée au client.

### Prérequis
- Une **clé API Gemini** (gratuite) : https://aistudio.google.com/apikey

### Déploiement — Option A : Dashboard (le plus simple)
1. Supabase → **Edge Functions** → **Create a function** → nom : `financial-advisor`.
2. Colle le contenu de `financial-advisor/index.ts` → **Deploy**.
3. Supabase → **Edge Functions** → **Secrets** (ou Project Settings → Edge Functions) →
   ajoute `GEMINI_API_KEY` = ta clé.

> `SUPABASE_URL` et `SUPABASE_ANON_KEY` sont injectés automatiquement par Supabase,
> rien à configurer pour eux.

### Déploiement — Option B : CLI
```bash
supabase functions deploy financial-advisor
supabase secrets set GEMINI_API_KEY=ta_cle
```

### Appel
`POST {SUPABASE_URL}/functions/v1/financial-advisor`
en-têtes : `Authorization: Bearer <jwt utilisateur>`, `apikey: <anon key>`.
Réponse : `{ analysis: {...}, advice: { summary, tips[] } | null }`.
Sans clé Gemini, `advice` est `null` mais l'`analysis` déterministe est toujours renvoyée.
