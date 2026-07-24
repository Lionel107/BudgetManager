# Edge Functions — Supabase

## `financial-advisor`

Analyse les 12 derniers mois de l'utilisateur, calcule saisonnalité + budget annuel
lissé (déterministe), et génère un accompagnement rédigé via Gemini.

**Clé Gemini = par utilisateur.** Chacun saisit SA clé dans les Réglages du PC ;
le client l'envoie dans le corps de la requête. Elle est utilisée côté serveur puis
jetée — **jamais stockée** (ni base, ni secret, ni log), jamais exposée aux autres.

### Prérequis
- Chaque utilisateur : une **clé API Gemini** (gratuite) : https://aistudio.google.com/apikey,
  à coller dans **Réglages → Clé API Gemini** de l'app.

### Déploiement — Dashboard (le plus simple)
1. Supabase → **Edge Functions** → **Create a function** → nom : `financial-advisor`.
2. Colle le contenu de `financial-advisor/index.ts` → **Deploy**.
3. **Aucun secret à définir** (`SUPABASE_URL` / `SUPABASE_ANON_KEY` sont injectés
   automatiquement ; la clé Gemini vient du client).

### Déploiement — CLI
```bash
supabase functions deploy financial-advisor
```

### Appel
`POST {SUPABASE_URL}/functions/v1/financial-advisor`
en-têtes : `Authorization: Bearer <jwt>`, `apikey: <anon key>` ; corps : `{ "geminiKey": "<clé de l'utilisateur>" }`.
Réponse : `{ analysis: {...}, advice: { summary, tips[] } | null }`.
Sans clé, `advice` est `null` mais l'`analysis` déterministe est toujours renvoyée.
