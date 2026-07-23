# PWA — Budget Manager (saisie mobile)

Web app légère pour **saisir des transactions depuis le téléphone** (iOS + Android) et les synchroniser via Supabase. Même backend que l'app Desktop (comptes/catégories partagés par utilisateur, isolés par la RLS).

## Contenu

| Fichier | Rôle |
|---|---|
| `index.html` | UI (connexion + saisie rapide + dernières transactions) |
| `app.js` | Logique : auth Supabase, chargement comptes/catégories, `rpc create_transaction`, liste |
| `config.js` | URL + anon key Supabase (**non versionné** ; voir `config.example.js`) |
| `manifest.webmanifest` + `icon.svg` + `sw.js` | Installable + chargement hors-ligne de la coquille |

Aucune étape de build : `supabase-js` est chargé depuis un CDN (ESM).

## Tester en local

Un service worker + les modules ES exigent `http(s)` (pas `file://`). Depuis le dossier `pwa/` :

```bash
python -m http.server 5173
# puis ouvrir http://localhost:5173
```

(ou toute autre commande de serveur statique : `npx serve`, etc.)

## Déployer (pour l'utiliser sur le téléphone)

Il faut du **HTTPS** (obligatoire pour une PWA installable et pour les téléphones). Le plus simple, gratuit :

- **Netlify** ou **Vercel** : glisser-déposer le dossier `pwa/` (ou connecter le repo), déploiement en HTTPS instantané.
- **Cloudflare Pages** / **GitHub Pages** : équivalent.

Une fois en ligne : ouvrir l'URL sur le téléphone → **« Ajouter à l'écran d'accueil »** → l'icône € apparaît comme une app.

> ⚠️ La `config.js` (URL + anon key) doit être présente sur l'hébergeur. La anon key est publique par design (protégée par la RLS), donc OK côté web ; elle est juste tenue hors du dépôt git par hygiène.

## Périmètre v1

Connexion / inscription · ajout rapide (compte, type, montant, catégorie, date) · 10 dernières transactions · déconnexion. La création de compte bancaire, budgets, etc. reste sur le Desktop (la PWA est pensée pour la saisie éclair en déplacement).

## À améliorer plus tard

- Icône **PNG** 192/512 px pour un rendu parfait de l'installation iOS (le SVG suffit sur Android).
- File d'attente hors-ligne (enregistrer sans réseau puis synchroniser) — non retenu en v1 (online-first).
