/**
 * 無狀態化的前端儲存層（ES module）。
 *
 * 三層：
 *  - sp_core   cookie        進度核心（server 簽章加密）— 寫 cookie，瀏覽器自動隨請求帶回
 *  - sp_history sessionStorage  兩條 AI 對話紀錄（雙向）— 每次請求隨 body._history 送 server 當 LLM context
 *  - sp_display sessionStorage  僅顯示用長文字（單向）— server→前端，供重新整理時顯示
 *
 * sessionStorage 關閉分頁即清空：進度（cookie）仍在，但 AI 對話記憶歸零（預期行為）。
 */

const CORE_COOKIE = 'sp_core';
const HISTORY_KEY = 'sp_history';
const DISPLAY_KEY = 'sp_display';
const MAX_AGE_SEC = 7200; // 與 server app.cookie.max-age-sec 對齊

function isHttps() {
  return typeof location !== 'undefined' && location.protocol === 'https:';
}

/** 讀 sp_core cookie 原始 token（base64url）。 */
export function readCoreCookie() {
  const prefix = CORE_COOKIE + '=';
  const match = document.cookie
    .split('; ')
    .find((c) => c.startsWith(prefix));
  return match ? decodeURIComponent(match.slice(prefix.length)) : '';
}

/** 寫 sp_core cookie（Path=/, SameSite=Lax, Max-Age=7200；https 自動加 Secure）。 */
export function writeCoreCookie(token) {
  if (!token) return;
  const parts = [
    `${CORE_COOKIE}=${encodeURIComponent(token)}`,
    'Path=/',
    `Max-Age=${MAX_AGE_SEC}`,
    'SameSite=Lax',
  ];
  if (isHttps()) parts.push('Secure');
  document.cookie = parts.join('; ');
}

/** 清除 sp_core cookie（進度重來）。 */
export function clearCoreCookie() {
  const parts = [`${CORE_COOKIE}=`, 'Path=/', 'Max-Age=0', 'SameSite=Lax'];
  if (isHttps()) parts.push('Secure');
  document.cookie = parts.join('; ');
}

function readJSON(key, fallback) {
  try {
    const raw = sessionStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function writeJSON(key, value) {
  try {
    sessionStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* 配額滿／被停用：靜默忽略，不影響主流程 */
  }
}

/** 讀兩條對話紀錄（供隨請求送 server）。 */
export function readHistory() {
  return readJSON(HISTORY_KEY, { npcChat: [], helperChat: [] });
}

/** 寫兩條對話紀錄（來自 server 回應 envelope）。 */
export function writeHistory(history) {
  if (!history) return;
  writeJSON(HISTORY_KEY, {
    npcChat: Array.isArray(history.npcChat) ? history.npcChat : [],
    helperChat: Array.isArray(history.helperChat) ? history.helperChat : [],
  });
}

/** 讀顯示文字快照。 */
export function readDisplay() {
  return readJSON(DISPLAY_KEY, {});
}

/** 合併寫入顯示文字快照。 */
export function writeDisplay(patch) {
  if (!patch) return;
  writeJSON(DISPLAY_KEY, { ...readDisplay(), ...patch });
}

/**
 * 套用回應中的 state envelope：寫 cookie（core）+ sessionStorage（history）。
 *
 * @param {{core?:string, history?:{npcChat:Array, helperChat:Array}}|null} env
 * @param {{withHistory?:boolean}} [opts] withHistory=false 時不覆寫 sessionStorage
 *   的歷史（GET 請求未送 _history，server 回顯空歷史，不該清掉前端持有的真值）。
 */
export function applyStateEnvelope(env, { withHistory = true } = {}) {
  if (!env) return;
  if (env.core) writeCoreCookie(env.core);
  if (withHistory && env.history) writeHistory(env.history);
}

/**
 * 從回應 data 抽取「僅顯示用」長文字存 sp_display（供重新整理後顯示）。
 * 只在非空時覆寫，避免串流中途的空字串蓋掉既有值。
 */
export function captureDisplay(data) {
  if (!data || !data.ui) return;
  const ui = data.ui;
  const patch = {};
  if (ui.npcText) patch.lastNpcText = ui.npcText;
  if (ui.summaryText) patch.lastSummaryText = ui.summaryText;
  if (ui.helper && ui.helper.lastBubble) patch.lastHelperBubble = ui.helper.lastBubble;
  if (Object.keys(patch).length) writeDisplay(patch);
}
