import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cfg = window.SUPABASE_CONFIG;
const supabase = createClient(cfg.url, cfg.anonKey);

// --- Raccourcis DOM ---
const $ = (id) => document.getElementById(id);
const show = (el) => el.classList.remove("hidden");
const hide = (el) => el.classList.add("hidden");

// --- État ---
let isSignUp = false;
let currentType = "EXPENSE";
let categories = [];
let mode = "ONCE"; // ONCE = transaction ponctuelle · RECUR = récurrente
let accountsData = [];        // comptes (avec solde) pour la section « Mes comptes »
let balancesRevealed = false; // soldes masqués par défaut

// ===================== AUTHENTIFICATION =====================

$("auth-toggle").addEventListener("click", () => {
  isSignUp = !isSignUp;
  $("auth-sub").textContent = isSignUp ? "Créer un compte" : "Connexion à ton compte";
  $("auth-btn").textContent = isSignUp ? "Créer le compte" : "Se connecter";
  $("auth-toggle").textContent = isSignUp
    ? "Déjà un compte ? Se connecter"
    : "Pas encore de compte ? En créer un";
  $("auth-msg").textContent = "";
});

$("auth-btn").addEventListener("click", async () => {
  const email = $("email").value.trim();
  const password = $("password").value;
  const msg = $("auth-msg");
  msg.className = "msg";
  if (!email || !password) { msg.textContent = "E-mail et mot de passe requis."; msg.classList.add("err"); return; }

  $("auth-btn").disabled = true;
  try {
    if (isSignUp) {
      const { error } = await supabase.auth.signUp({ email, password });
      if (error) throw error;
      msg.textContent = "Compte créé ! Tu peux te connecter.";
      msg.classList.add("ok");
    } else {
      const { error } = await supabase.auth.signInWithPassword({ email, password });
      if (error) throw error;
      // onAuthStateChange bascule l'UI
    }
  } catch (e) {
    msg.textContent = friendlyError(e);
    msg.classList.add("err");
  } finally {
    $("auth-btn").disabled = false;
  }
});

$("logout").addEventListener("click", async () => {
  await supabase.auth.signOut();
});

function friendlyError(e) {
  const m = (e?.message || "").toLowerCase();
  if (m.includes("invalid login")) return "E-mail ou mot de passe incorrect.";
  if (m.includes("already registered")) return "Un compte existe déjà avec cet e-mail.";
  if (m.includes("email not confirmed")) return "E-mail non confirmé.";
  if (m.includes("6 characters")) return "Mot de passe trop court (6 caractères min).";
  return e?.message || "Une erreur est survenue.";
}

// ===================== TYPE (dépense / revenu) =====================

function setType(type) {
  currentType = type;
  $("type-expense").className = type === "EXPENSE" ? "on-expense" : "";
  $("type-income").className = type === "INCOME" ? "on-income" : "";
  renderCategories();
}
$("type-expense").addEventListener("click", () => setType("EXPENSE"));
$("type-income").addEventListener("click", () => setType("INCOME"));

// ===================== MODE ponctuelle / récurrente =====================

function setMode(m) {
  mode = m;
  $("mode-once").className = m === "ONCE" ? "on-mode" : "";
  $("mode-recur").className = m === "RECUR" ? "on-mode" : "";
  $("freq-wrap").classList.toggle("hidden", m !== "RECUR");
  $("date-label").textContent = m === "RECUR" ? "Première échéance" : "Date";
  $("save").textContent = m === "RECUR" ? "Créer la récurrence" : "Enregistrer";
}
$("mode-once").addEventListener("click", () => setMode("ONCE"));
$("mode-recur").addEventListener("click", () => setMode("RECUR"));

// ===================== MES COMPTES (soldes masqués + bouton œil) =====================

function renderAccounts() {
  const el = $("accounts-list");
  if (!accountsData.length) {
    el.innerHTML = `<p class="muted">Aucun compte. Crée-en un sur le PC.</p>`;
    return;
  }
  el.innerHTML = accountsData.map((a) => {
    const cur = a.currency_code || "EUR";
    const bal = balancesRevealed
      ? `<span class="bal">${fmt(a.balance)} ${escapeHtml(cur)}</span>`
      : `<span class="bal hidden-val">••••••</span>`;
    return `<div class="acct"><span class="an">${escapeHtml(a.name)}</span>${bal}</div>`;
  }).join("");
}

const EYE = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z"/><circle cx="12" cy="12" r="3"/></svg>';
const EYE_OFF = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.94 10.94 0 0 1 12 20C5 20 1 12 1 12a18.5 18.5 0 0 1 5.06-5.94M9.9 4.24A9 9 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19M1 1l22 22"/></svg>';
function updateEye() {
  $("eye-ic").innerHTML = balancesRevealed ? EYE_OFF : EYE;
  $("eye-tx").textContent = balancesRevealed ? "Masquer" : "Afficher";
}
$("toggle-balances").addEventListener("click", () => {
  balancesRevealed = !balancesRevealed;
  renderAccounts();
  updateEye();
});

function renderCategories() {
  const sel = $("category");
  const list = categories.filter((c) => c.category_type === currentType);
  sel.innerHTML =
    `<option value="">— aucune —</option>` +
    list.map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join("");
}

// ===================== CHARGEMENT DES DONNÉES =====================

async function loadAppData() {
  const { data: { user } } = await supabase.auth.getUser();
  $("user-email").textContent = user?.email || "";

  const [acc, cat] = await Promise.all([
    supabase.from("accounts").select("id,name,balance,currency_code").eq("is_active", true).order("display_order"),
    supabase.from("categories").select("id,name,category_type").eq("is_active", true).order("display_order"),
  ]);

  // Diagnostics (visibles dans la console F12)
  if (acc.error) console.error("[accounts] erreur:", acc.error);
  if (cat.error) console.error("[categories] erreur:", cat.error);
  console.log("[accounts] chargés:", acc.data?.length ?? 0, "| [categories] chargées:", cat.data?.length ?? 0);

  const accSel = $("account");
  accSel.innerHTML = (acc.data || [])
    .map((a) => `<option value="${a.id}">${escapeHtml(a.name)}</option>`)
    .join("") || `<option value="">(aucun compte — crée-en un sur le PC)</option>`;

  accountsData = acc.data || [];
  renderAccounts();
  updateEye();

  categories = cat.data || [];
  renderCategories();

  $("date").value = new Date().toISOString().slice(0, 10);
  await loadRecent();
}

async function loadRecent() {
  const { data } = await supabase
    .from("transactions")
    .select("id,title,amount,transaction_type,date")
    .order("date", { ascending: false })
    .limit(10);
  const el = $("recent");
  if (!data || data.length === 0) { el.innerHTML = `<p class="muted">Aucune transaction.</p>`; return; }
  el.innerHTML = data.map((t) => {
    const inc = t.transaction_type === "INCOME";
    const sign = inc ? "+" : "-";
    const cls = inc ? "inc" : "exp";
    const d = (t.date || "").slice(0, 10);
    return `<div class="txn"><span>${escapeHtml(t.title)}<br><span class="muted">${d}</span></span>
      <span class="amt ${cls}">${sign}${fmt(t.amount)} €</span></div>`;
  }).join("");
}

// ===================== ENREGISTREMENT =====================

$("save").addEventListener("click", async () => {
  const msg = $("save-msg");
  msg.className = "msg";
  const accountId = $("account").value;
  const amount = parseFloat($("amount").value);
  const title = $("title").value.trim();
  const categoryId = $("category").value || null;
  const dateStr = $("date").value;

  if (!accountId) { msg.textContent = "Choisis un compte."; msg.classList.add("err"); return; }
  if (!amount || amount <= 0) { msg.textContent = "Montant invalide."; msg.classList.add("err"); return; }
  if (!title) { msg.textContent = "Ajoute un libellé."; msg.classList.add("err"); return; }

  $("save").disabled = true;
  try {
    // --- Récurrente : insertion dans recurring_transactions ---
    if (mode === "RECUR") {
      const { error } = await supabase.from("recurring_transactions").insert({
        title,
        amount,
        account_id: Number(accountId),
        category_id: categoryId ? Number(categoryId) : null,
        frequency_type: $("frequency").value,
        repeat_interval: 1,
        start_date: dateStr,
        next_due_date: dateStr,
        transaction_type: currentType,
        is_active: true,
      });
      if (error) throw error;
      msg.textContent = "Récurrence créée ✓ (elle se générera automatiquement)";
      msg.classList.add("ok");
      $("amount").value = "";
      $("title").value = "";
      $("save").disabled = false;
      return;
    }

    const isoDate = new Date(dateStr + "T00:00:00Z").toISOString();
    const { error } = await supabase.rpc("create_transaction", {
      p_account_id: Number(accountId),
      p_title: title,
      p_amount: amount,
      p_type: currentType,
      p_date: isoDate,
      p_category_id: categoryId ? Number(categoryId) : null,
    });
    if (error) throw error;
    msg.textContent = "Enregistré ✓";
    msg.classList.add("ok");
    $("amount").value = "";
    $("title").value = "";
    await loadRecent();
  } catch (e) {
    msg.textContent = e?.message || "Échec de l'enregistrement.";
    msg.classList.add("err");
  } finally {
    $("save").disabled = false;
  }
});

// ===================== COPILOTE (chat IA) =====================
// Appelle l'Edge Function `analyst` (mode chat), qui recharge elle-même tout le
// profil + budget à chaque message. La clé Gemini reste sur CE téléphone
// (localStorage) : envoyée à chaque appel, jamais stockée côté serveur.

const KEY_STORE = "bm_gemini_key";
const HISTORY_STORE = "bm_chat_history";
const SUGGESTIONS = [
  "Comment je m'en sors ce mois-ci ?",
  "Je peux me faire un resto ce week-end ?",
  "Où je peux vraiment économiser ?",
];
let chatHistory = [];   // [{ role: "user" | "assistant", text }]
let chatSending = false;
let askingKey = false;

const store = {
  get(k) { try { return localStorage.getItem(k) || ""; } catch { return ""; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch { /* stockage indisponible */ } },
};
try { chatHistory = JSON.parse(sessionStorage.getItem(HISTORY_STORE) || "[]"); } catch { chatHistory = []; }
function saveHistory() {
  try { sessionStorage.setItem(HISTORY_STORE, JSON.stringify(chatHistory.slice(-30))); } catch { /* ignore */ }
}

/** Markdown léger et sûr : échappement HTML puis **gras**, *italique*, listes, retours à la ligne. */
function renderMd(text) {
  const lines = escapeHtml(text).split("\n");
  let html = "", inList = false;
  for (const raw of lines) {
    const line = raw.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>").replace(/(^|\s)\*(\S.+?)\*/g, "$1<em>$2</em>");
    const li = line.match(/^\s*(?:[-*•]|\d+\.)\s+(.*)$/);
    if (li) { if (!inList) { html += "<ul>"; inList = true; } html += `<li>${li[1]}</li>`; continue; }
    if (inList) { html += "</ul>"; inList = false; }
    html += line.trim() ? `${line}<br>` : "<br>";
  }
  if (inList) html += "</ul>";
  return html.replace(/(<br>)+$/, "");
}

function renderChat() {
  const body = $("chat-body");
  if (askingKey || !store.get(KEY_STORE)) {
    askingKey = true;
    body.innerHTML = `
      <div class="bubble bot">Salut ! 👋 Je suis ton copilote budget. Pour discuter, j'ai besoin de ta clé Gemini (la même que dans l'app PC).</div>
      <div class="keybox">
        Crée-la gratuitement sur <a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener">aistudio.google.com/apikey</a>, puis colle-la ici. Elle reste sur ce téléphone.
        <input id="key-input" type="password" placeholder="AIza…" autocomplete="off" style="margin-top:10px" value="${escapeHtml(store.get(KEY_STORE))}" />
        <button id="key-save" type="button">Enregistrer</button>
      </div>`;
    $("key-save").addEventListener("click", () => {
      const k = $("key-input").value.trim();
      if (!k) return;
      store.set(KEY_STORE, k);
      askingKey = false;
      renderChat();
    });
    return;
  }

  let html = "";
  if (chatHistory.length === 0) {
    html += `<div class="bubble bot">Salut ! 👋 Pose-moi n'importe quelle question sur ton budget : je vois ton profil, tes budgets, tes objectifs et tes dépenses.</div>`;
    html += `<div class="chips">${SUGGESTIONS.map((s) => `<button class="chip" type="button">${escapeHtml(s)}</button>`).join("")}</div>`;
  }
  for (const m of chatHistory) {
    const cls = m.role === "user" ? "me" : `bot${m.error ? " err" : ""}`;
    html += `<div class="bubble ${cls}">${m.role === "user" ? escapeHtml(m.text) : renderMd(m.text)}</div>`;
  }
  if (chatSending) html += `<div class="bubble bot"><span class="typing"><i></i><i></i><i></i></span></div>`;
  body.innerHTML = html;
  body.querySelectorAll(".chip").forEach((c) => c.addEventListener("click", () => sendChat(c.textContent)));
  body.scrollTop = body.scrollHeight;
}

async function sendChat(text) {
  const question = (text ?? $("chat-input").value).trim();
  if (!question || chatSending) return;
  if (!store.get(KEY_STORE)) { askingKey = true; renderChat(); return; }
  askingKey = false;
  $("chat-input").value = "";
  // Historique envoyé = tours précédents (sans les messages d'erreur)
  const history = chatHistory.filter((m) => !m.error).map(({ role, text }) => ({ role, text }));
  chatHistory.push({ role: "user", text: question });
  chatSending = true;
  renderChat();
  try {
    const { data, error } = await supabase.functions.invoke("analyst", {
      body: { geminiKey: store.get(KEY_STORE), mode: "chat", history, question },
    });
    if (error) {
      let msg = error.message;
      try { msg = (await error.context.json()).error || msg; } catch { /* corps non JSON */ }
      throw new Error(msg);
    }
    if (data?.error) throw new Error(data.error);
    chatHistory.push({ role: "assistant", text: data?.reply || "…" });
  } catch (e) {
    const m = String(e?.message || e);
    const hint = /401|403|API key|clé/i.test(m) ? " Vérifie ta clé Gemini (icône 🔑 en haut)." : "";
    chatHistory.push({ role: "assistant", text: `Oups, je n'ai pas pu répondre : ${m}${hint}`, error: true });
  } finally {
    chatSending = false;
    saveHistory();
    renderChat();
  }
}

function openChat() {
  $("chat").classList.add("open");
  $("chat").setAttribute("aria-hidden", "false");
  renderChat();
  if (!askingKey) setTimeout(() => $("chat-input").focus(), 300);
}
function closeChat() {
  $("chat").classList.remove("open");
  $("chat").setAttribute("aria-hidden", "true");
  $("chat-input").blur();
}

$("chat-fab").addEventListener("click", openChat);
$("chat-close").addEventListener("click", closeChat);
$("chat-key").addEventListener("click", () => { askingKey = true; renderChat(); });
$("chat-send").addEventListener("click", () => sendChat());
$("chat-input").addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); sendChat(); } });

// ===================== UTILITAIRES =====================

function fmt(n) { return Number(n).toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// ===================== ROUTAGE AUTH =====================

async function route(session) {
  hide($("loading"));
  if (session) {
    hide($("auth")); show($("app")); show($("chat-fab"));
    await loadAppData();
  } else {
    hide($("app")); show($("auth")); hide($("chat-fab")); closeChat();
    chatHistory = []; saveHistory();
  }
}

supabase.auth.onAuthStateChange((_event, session) => { route(session); });
supabase.auth.getSession().then(({ data }) => route(data.session));

// PWA : service worker
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js").catch(() => {});
}
