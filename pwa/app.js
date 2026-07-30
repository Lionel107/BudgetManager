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
    supabase.from("accounts").select("id,name").eq("is_active", true).order("display_order"),
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

// ===================== UTILITAIRES =====================

function fmt(n) { return Number(n).toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// ===================== ROUTAGE AUTH =====================

async function route(session) {
  hide($("loading"));
  if (session) {
    hide($("auth")); show($("app"));
    await loadAppData();
  } else {
    hide($("app")); show($("auth"));
  }
}

supabase.auth.onAuthStateChange((_event, session) => { route(session); });
supabase.auth.getSession().then(({ data }) => route(data.session));

// PWA : service worker
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js").catch(() => {});
}
