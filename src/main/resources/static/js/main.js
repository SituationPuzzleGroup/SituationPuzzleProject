import { api } from './api/client.js';
import { readDisplay } from './api/stateStore.js';
import { initScale } from './rwd/scale.js';
import { initHelperDrag } from './ui/helperDrag.js';

const $ = (id) => document.getElementById(id);

let state = null;
let busy = false;
let streaming = false;
/** 館長是否在「說話」循環 */
let npcTalking = false;
/** 選項回答：字出完後最多再動幾「下」嘴（一開一合算一下） */
const TALK_MAX_BEATS_AFTER_TEXT = 5;
/** 完成四輪後結局／離館台詞：固定講幾下 */
const TALK_BEATS_ENDING = 10;
/** 一下 ≈ 張嘴+閉嘴（與 npc-talk.webp 幀時長對齊） */
const TALK_BEAT_MS = 250;
/** @type {ReturnType<typeof setTimeout> | null} */
let talkStopTimer = null;

/** 對話框打字機（非 SSE 串流的館長台詞）。
 *  調速（擇一，皆免重新打包）：
 *   1. 網址參數：http://…:8080/?typeMs=40（最簡單，直接改網址列按 Enter）
 *   2. sessionStorage.setItem('typeMs','40')（注意：key 由你設定才存在；getItem 回 null＝尚未設定）
 *  數值＝每字毫秒（越大越慢）；移除覆蓋請刪掉網址參數或 sessionStorage.removeItem('typeMs')。 */
function readTypeMsOverride() {
  try {
    const raw = new URLSearchParams(location.search).get('typeMs');
    // 注意：get() 沒參數時回 null，而 Number(null)===0 會被誤判成「要 0ms」，必須先排除
    if (raw !== null && raw !== '') {
      const q = Number(raw);
      if (Number.isFinite(q) && q >= 0) return { v: q, src: '網址參數' };
    }
  } catch (e) { /* 無 location 環境 */ }
  try {
    const raw = sessionStorage.getItem('typeMs');
    if (raw !== null && raw !== '') {
      const s = Number(raw);
      if (Number.isFinite(s) && s >= 0) return { v: s, src: 'sessionStorage' };
    }
  } catch (e) { /* 無 sessionStorage 環境 */ }
  return null;
}
const TYPEWRITER_MS_OVERRIDE = readTypeMsOverride();
const TYPEWRITER_MS_PER_CHAR = TYPEWRITER_MS_OVERRIDE != null ? TYPEWRITER_MS_OVERRIDE.v : 40; // 預設 40ms/字
console.info(`[字幕速度] 每字 ${TYPEWRITER_MS_PER_CHAR}ms（來源：${TYPEWRITER_MS_OVERRIDE ? TYPEWRITER_MS_OVERRIDE.src : '預設'}）`);
/** @type {ReturnType<typeof setTimeout> | null} */
let typewriterTimer = null;
let typewriterFull = '';
let typewriterIndex = 0;
let typewriterRunning = false;
/** @type {null | (() => void)} */
let typewriterOnDone = null;
/** AI 串流顯示節奏：點擊對話框跳過（顯示全文）；null＝目前無節奏顯示進行中 */
let streamPaceSkip = null;

const ADVANCE_PHASES = new Set([
  'INTRO',
  'STORY_RESULT',
  'OPTION_REPLY',
  'STORY_SUMMARY',
  'ENDING',
]);

const els = {
  phase: $('hud-phase'),
  score: $('hud-score'),
  dialogue: $('dialogue-text'),
  speaker: $('speaker-name'),
  tip: $('click-tip'),
  continueHint: $('continue-hint'),
  options: $('options-panel'),
  menu: $('menu-panel'),
  endingPanel: $('ending-panel'),
  endingDossier: $('ending-dossier'),
  title: $('title-panel'),
  menuLeft: $('menu-panel-left'),
  originDialog: $('origin-dialog'),
  dialogueBox: $('dialogue-box'),
  helperPanel: $('helper-panel'),
  helperLog: $('helper-log'),
  hintLevel: $('hint-level'),
  loading: $('loading-overlay'),
  loadingText: $('loading-text'),
  overlay: $('ui-overlay'),
  helperDock: $('helper-dock'),
  gameShell: $('game-shell'),
  npcSprite: $('npc-sprite'),
};

const NPC_TALK_SRC = '/assets/npc/npc-talk.webp';

/** 依階段切換館長立繪 idle（動態 WebP 自帶眨眼循環） */
function npcSpriteForPhase(phase) {
  switch (phase) {
    case 'TITLE':
    case 'FINISHED':
      return '/assets/npc/npc-1.webp';
    case 'INTRO':
    case 'STORY_MENU':
    case 'LEAVE_HINT':
      return '/assets/npc/npc-1.webp';
    case 'STORY_RESULT':
      return '/assets/npc/npc-2.webp';
    case 'SELECT_OPTION':
    case 'ASK_PROMPT':
      return '/assets/npc/npc-3.webp';
    case 'OPTION_REPLY':
    case 'STORY_SUMMARY':
      return '/assets/npc/npc-2.webp';
    case 'ENDING':
      return '/assets/npc/npc-4.webp';
    default:
      return '/assets/npc/npc-1.webp';
  }
}

/** 記錄上次閒置立繪，用於百葉窗登場判斷（說話循環替換不觸發） */
let lastIdleSpriteSrc = null;

/** 依階段切換立繪；若 npcTalking 則維持說話循環 webp */
function setNpcSprite(phase) {
  if (!els.npcSprite) return;
  const idleSrc = npcSpriteForPhase(phase);
  const src = npcTalking ? NPC_TALK_SRC : idleSrc;
  if (els.npcSprite.getAttribute('src') !== src) {
    els.npcSprite.setAttribute('src', src);
  }
  // 標題／結局完成頁不顯示立繪：純背景＋標題卡，進入遊戲時 NPC 再百葉窗登場
  const npcArea = els.npcSprite.parentElement;
  const titleLike = phase === 'TITLE' || phase === 'FINISHED';
  const wasHidden = npcArea ? npcArea.classList.contains('npc-hidden') : false;
  if (npcArea) npcArea.classList.toggle('npc-hidden', titleLike);
  // 百葉窗登場：初次載入、閒置立繪變更、或從標題隱藏轉為顯示時
  if (!npcTalking && (idleSrc !== lastIdleSpriteSrc || wasHidden)) {
    lastIdleSpriteSrc = idleSrc;
    retriggerBlinds(els.npcSprite);
  }
  if (els.gameShell) {
    els.gameShell.classList.toggle('is-title', titleLike);
  }
}

/** 重播立繪百葉窗動畫（CSS blinds-in class） */
function retriggerBlinds(img) {
  img.classList.remove('blinds-in');
  void img.offsetWidth; // 強制 reflow 以重啟 CSS 動畫
  img.classList.add('blinds-in');
}

function clearTalkStopTimer() {
  if (talkStopTimer != null) {
    clearTimeout(talkStopTimer);
    talkStopTimer = null;
  }
}

/**
 * 說話循環 on/off。
 * 字出完後可再排程「最多 N 下」自動停；點擊繼續也會立刻停。
 */
function setNpcTalking(talking) {
  if (!talking) {
    clearTalkStopTimer();
  }
  npcTalking = !!talking;
  const phase = state?.phase || 'OPTION_REPLY';
  setNpcSprite(phase);
}

/** talk 進行中：N 下後自動回 idle（點擊可提前停） */
function scheduleTalkStopAfterBeats(beats = TALK_MAX_BEATS_AFTER_TEXT) {
  clearTalkStopTimer();
  if (!npcTalking) return;
  const n = Math.max(0, beats | 0);
  if (n === 0) {
    setNpcTalking(false);
    return;
  }
  talkStopTimer = setTimeout(() => {
    talkStopTimer = null;
    setNpcTalking(false);
  }, n * TALK_BEAT_MS);
}

/** 立刻開始說話，並在固定 N 下後停止（結局台詞用） */
function startNpcTalkForBeats(beats) {
  setNpcTalking(true);
  scheduleTalkStopAfterBeats(beats);
}

function clearTypewriterTimer() {
  if (typewriterTimer != null) {
    clearTimeout(typewriterTimer);
    typewriterTimer = null;
  }
}

/**
 * 停止打字機。
 * @param {{ complete?: boolean }} [opts] complete=true 時立刻顯示全文
 */
function stopTypewriter({ complete = false } = {}) {
  clearTypewriterTimer();
  const wasRunning = typewriterRunning;
  typewriterRunning = false;
  if (complete && typewriterFull) {
    els.dialogue.textContent = typewriterFull;
    typewriterIndex = typewriterFull.length;
  }
  const cb = typewriterOnDone;
  typewriterOnDone = null;
  if (wasRunning && cb) cb();
}

/**
 * 逐字顯示對話（保留換行段落；pre-wrap）。
 * @param {string} text
 * @param {{ msPerChar?: number, onDone?: () => void }} [opts]
 */
function typeDialogue(text, opts = {}) {
  stopTypewriter({ complete: false });
  typewriterFull = text == null ? '' : String(text);
  typewriterIndex = 0;
  typewriterOnDone = typeof opts.onDone === 'function' ? opts.onDone : null;
  const ms = opts.msPerChar != null ? opts.msPerChar : TYPEWRITER_MS_PER_CHAR;

  if (!typewriterFull) {
    els.dialogue.textContent = '';
    if (typewriterOnDone) {
      const cb = typewriterOnDone;
      typewriterOnDone = null;
      cb();
    }
    return;
  }

  // 極短字串直接顯示
  if (typewriterFull.length <= 2) {
    els.dialogue.textContent = typewriterFull;
    if (typewriterOnDone) {
      const cb = typewriterOnDone;
      typewriterOnDone = null;
      cb();
    }
    return;
  }

  typewriterRunning = true;
  els.dialogue.textContent = '';

  const step = () => {
    if (!typewriterRunning) return;
    // 一次 1 字（長文短文速度一致）
    typewriterIndex = Math.min(typewriterFull.length, typewriterIndex + 1);
    els.dialogue.textContent = typewriterFull.slice(0, typewriterIndex);
    if (els.dialogueBox) {
      els.dialogueBox.scrollTop = els.dialogueBox.scrollHeight;
    }
    if (typewriterIndex >= typewriterFull.length) {
      typewriterRunning = false;
      typewriterTimer = null;
      const cb = typewriterOnDone;
      typewriterOnDone = null;
      if (cb) cb();
      return;
    }
    typewriterTimer = setTimeout(step, ms);
  };
  step();
}

/** 依階段給較自然的底部提示（避免生硬 UI 用語） */
function continueHintForPhase(phase) {
  switch (phase) {
    case 'INTRO':
      return '聽完開場了嗎？輕點此處繼續——接下來是回家的規則。卡住時可找右下角小迴紋。';
    case 'STORY_RESULT':
      return '故事說到這裡。準備好提問時，點一下繼續。';
    case 'OPTION_REPLY':
      return '館長的回答告一段落——點這裡，再往下走。';
    case 'STORY_SUMMARY':
      return '本則暫告段落。點一下，我們回選單看看。';
    case 'ENDING':
      return '右側「故事與真實事件」可閱讀簡介與連結。讀完後點這裡離開。';
    case 'SELECT_OPTION':
    case 'ASK_PROMPT':
      return '左右兩側的問題，挑一個你最在意的——點了，館長才會開口。想不到答案時，右下角小迴紋可以給點方向（不扣分）。';
    case 'STORY_MENU':
      return '右側故事卡：走完四則，回去的路才會開。選一則開始吧。';
    case 'LEAVE_HINT':
      return '四則已走完。可點「聆聽結局」——門後就是回去的方向。';
    default:
      return '輕點對話框，故事會接著往下。';
  }
}

/**
 * @param {boolean} visible
 * @param {string} phase
 * @param {'advance'|'options'|'menu'} [kind]
 */
function setContinueHint(visible, phase, kind = 'advance') {
  if (!els.continueHint) return;
  if (!visible) {
    els.continueHint.classList.add('hidden');
    els.continueHint.classList.remove('hint-options', 'hint-menu', 'hint-advance');
    els.continueHint.textContent = '';
    return;
  }
  els.continueHint.textContent = continueHintForPhase(phase);
  els.continueHint.classList.remove('hint-options', 'hint-menu', 'hint-advance', 'hidden');
  els.continueHint.classList.add(
    kind === 'options' ? 'hint-options' : kind === 'menu' ? 'hint-menu' : 'hint-advance'
  );
}

function setBusy(v, message, { soft = false } = {}) {
  busy = v;
  const msg = message || '伺服器處理中，請稍候…';
  if (els.loadingText) els.loadingText.textContent = msg;
  // 串流中用 soft：不蓋住對話框，只擋按鈕
  if (els.loading) {
    if (v && !soft) els.loading.classList.remove('hidden');
    else els.loading.classList.add('hidden');
  }
  if (els.overlay) els.overlay.classList.toggle('is-busy', v);
  if (els.helperDock) els.helperDock.classList.toggle('is-busy', v && !streaming);
  document.body.style.cursor = v ? 'wait' : '';

  document.querySelectorAll('.opt-btn, .menu-btn, .btn').forEach((el) => {
    if (el.id === 'helper-toggle') return;
    if (v) {
      el.dataset.wasDisabled = el.disabled ? '1' : '0';
      el.disabled = true;
    } else if (el.dataset.wasDisabled !== undefined) {
      el.disabled = el.dataset.wasDisabled === '1';
      delete el.dataset.wasDisabled;
    } else {
      el.disabled = false;
    }
  });
}

function applyState(data, { preserveDialogue = false } = {}) {
  if (!data) return;
  const prevPhase = state?.phase;
  state = data;
  els.phase.textContent = data.phase || '—';
  // 計分以「本則」為主；加總僅輔助顯示
  if (data.currentRound) {
    const thr = data.ui?.truthThreshold != null ? data.ui.truthThreshold : 60;
    els.score.textContent =
      `本則 ${data.storyScore ?? 0}（揭謎≥${thr}）· ${data.currentRound}/${data.maxRounds}`;
  } else if (data.phase === 'ENDING' || data.phase === 'FINISHED' || data.phase === 'LEAVE_HINT') {
    const et = data.endingType === 'TRUE' ? '真結局' : data.endingType === 'NORMAL' ? '普通結局' : '';
    els.score.textContent =
      `揭謎 ${data.truthRevealedCount ?? 0}/${data.completedCount ?? 0}` +
      (et ? ` · ${et}` : '') +
      ` · 加總 ${data.totalScore ?? 0}`;
  } else {
    els.score.textContent = `揭謎 ${data.truthRevealedCount ?? 0}/${data.completedCount ?? 0} · 加總 ${data.totalScore ?? 0}`;
  }

  const ui = data.ui || {};
  if (data.phase === 'ENDING') {
    els.speaker.textContent = data.endingType === 'TRUE' ? '真結局' : '普通結局';
  } else {
    els.speaker.textContent = data.phase === 'SELECT_OPTION' ? '系統' : '館長';
  }
  setNpcSprite(data.phase);

  // 館長長台詞：talk 10 下
  // 含開場、規則、故事結果敘述（如粉紅房子）、離館、結局
  const enteredSpeechTalk =
    !preserveDialogue &&
    prevPhase !== data.phase &&
    (data.phase === 'INTRO' ||
      data.phase === 'STORY_RESULT' ||
      data.phase === 'ENDING' ||
      data.phase === 'LEAVE_HINT' ||
      (data.phase === 'STORY_MENU' && prevPhase === 'INTRO'));
  if (enteredSpeechTalk) {
    startNpcTalkForBeats(TALK_BEATS_ENDING);
  }

  if (ui.helper?.hintLevel) {
    els.hintLevel.value = ui.helper.hintLevel;
  }

  els.title.classList.toggle('hidden', data.phase !== 'TITLE' && data.phase !== 'FINISHED');
  const showMenu = data.phase === 'STORY_MENU' || data.phase === 'LEAVE_HINT';
  els.menu.classList.toggle('hidden', !showMenu);
  if (els.menuLeft) els.menuLeft.classList.toggle('hidden', !showMenu);
  const endBtn = $('btn-ending');
  if (endBtn) endBtn.classList.toggle('hidden', data.phase !== 'LEAVE_HINT');
  const showOpts = data.phase === 'SELECT_OPTION' || data.phase === 'ASK_PROMPT';
  els.options.classList.toggle('hidden', !showOpts);
  const showEnding =
    data.phase === 'ENDING' || data.phase === 'FINISHED';
  if (els.endingPanel) {
    els.endingPanel.classList.toggle('hidden', !showEnding);
  }
  if (showEnding) {
    renderEndingDossier(ui.endingDossier || [], ui.endingType || data.endingType);
  } else if (els.endingDossier) {
    els.endingDossier.innerHTML = '';
  }

  if (showMenu) renderMenu(data.menu || []);
  if (showOpts) renderOptions(ui.options || []);

  const canAdvance = ADVANCE_PHASES.has(data.phase);
  const showContinue = canAdvance && !showOpts;
  els.dialogueBox.classList.toggle('no-advance', !canAdvance || showOpts);

  const applyContinueUi = () => {
    // 右下角 ▼：僅「點對話框繼續」時顯示
    els.tip.classList.toggle('hidden', !showContinue);
    if (streaming) {
      setContinueHint(false);
    } else if (showOpts) {
      setContinueHint(true, data.phase, 'options');
    } else if (showMenu) {
      setContinueHint(true, data.phase, 'menu');
    } else if (showContinue) {
      setContinueHint(true, data.phase, 'advance');
    } else {
      setContinueHint(false);
    }
  };

  if (!preserveDialogue && (data.phase === 'TITLE' || data.phase === 'FINISHED')) {
    stopTypewriter({ complete: false });
    els.dialogue.textContent = data.phase === 'FINISHED'
      ? '感謝遊玩。可重新進入遊戲。'
      : '點擊「進入遊戲」開始。';
    setContinueHint(false);
    els.tip.classList.add('hidden');
  } else if (!preserveDialogue) {
    // 無狀態化：lastNpcText/lastSummaryText 不在 cookie；GET 重建時為 null，
    // 改從 sessionStorage(sp_display) 取上次顯示的文字，避免 F5 後對話框只剩「……」。
    const disp = readDisplay();
    const full = ui.npcText || ui.summaryText || disp.lastNpcText || disp.lastSummaryText || '……';
    // 打字機進行中：先不顯示「繼續」提示；點一下可跳過打字
    els.tip.classList.add('hidden');
    setContinueHint(false);
    typeDialogue(full, {
      onDone: () => {
        if (state?.phase === data.phase) applyContinueUi();
      },
    });
  } else {
    // 串流後保留全文等：直接刷新提示
    applyContinueUi();
  }
}

function bindButton(btn, handler) {
  btn.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (busy) {
      if (!streaming) setBusy(true, '仍在等待伺服器回應，請稍候…');
      return;
    }
    handler(e);
  });
}

function renderMenu(menu) {
  els.menu.innerHTML = '';
  if (els.menuLeft) els.menuLeft.innerHTML = '';
  // 前半故事在右欄（故事1、2），後半在左欄（故事3、4）
  const rightCount = Math.ceil(menu.length / 2);
  menu.forEach((item) => {
    // 包一層：故事卡 + 後方「聆聽故事起源」籤條（z-index 重疊）
    const wrap = document.createElement('div');
    wrap.className = 'menu-item';
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'menu-btn';
    if (item.locked) btn.classList.add('locked');
    if (item.completed) btn.classList.add('done');
    let label = `故事${item.order} · ${item.title}`;
    if (item.completed) {
      label += ` ✓ ${item.score ?? 0}分`;
      label += item.truthRevealed ? ' · 已揭謎' : ' · 未揭謎';
    } else if (item.locked) {
      label += ' 🔒';
    }
    btn.textContent = label;

    if (!item.locked && !item.completed) {
      bindButton(btn, () => act(() => api.selectStory(item.order), '進入故事中…'));
    }
    wrap.appendChild(btn);

    // 本則玩過：卡片後下緣露出「聆聽故事起源」，點擊以 Dialog 顯示該則真實事件
    if (item.completed) {
      const tab = document.createElement('button');
      tab.type = 'button';
      tab.className = 'origin-tab';
      tab.textContent = '聆聽故事起源';
      bindButton(tab, () =>
        act(async () => {
          const r = await api.realCase(item.order);
          openOriginDialog(item, r);
          return null; // 不重繪選單
        }, '讀取真實事件…'));
      wrap.appendChild(tab);
    }
    (item.order <= rightCount ? els.menu : (els.menuLeft ?? els.menu)).appendChild(wrap);
  });

  if (state?.phase === 'LEAVE_HINT') {
    const endBtn = $('btn-ending');
    endBtn.classList.remove('hidden');
    if (!endBtn.dataset.bound) {
      endBtn.dataset.bound = '1';
      bindButton(endBtn, () => act(async () => {
        try {
          return await api.finish();
        } catch (e) {
          throw e;
        }
      }, '結算結局中…'));
    }
  }
}

/**
 * 「聆聽故事起源」Dialog：顯示單則故事的真實事件（沿用結局檔案卡設計，連結為按鈕）
 * @param {{order:number, title:string}} item
 * @param {{text:string, url?:string, label?:string}} r
 */
function openOriginDialog(item, r) {
  const dialog = $('origin-dialog');
  if (!dialog) return;
  $('origin-dialog-title').textContent = `故事${item.order}〈${item.title}〉· 真實事件`;
  const body = $('origin-dialog-body');
  body.innerHTML = '';
  const text = document.createElement('p');
  text.className = 'origin-dialog-text';
  text.textContent = r.text;
  body.appendChild(text);
  if (r.url) {
    const foot = document.createElement('div');
    foot.className = 'origin-dialog-foot';
    const link = document.createElement('a');
    link.className = 'ending-link'; // 結局檔案卡的按鈕樣式
    link.href = String(r.url);
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    link.textContent = r.label || '延伸閱讀';
    foot.appendChild(link);
    body.appendChild(foot);
  }
  dialog.classList.remove('hidden');
}

function renderOptions(options) {
  els.options.innerHTML = '';
  options.forEach((o) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'opt-btn';
    btn.textContent = o.text;
    bindButton(btn, () => streamNpcAnswer(o.id));
    els.options.appendChild(btn);
  });
}

/**
 * 結局檔案：各則故事＋真實事件簡介與連結
 * @param {Array<Record<string, unknown>>} dossier
 * @param {string} [endingType]
 */
function renderEndingDossier(dossier, endingType) {
  if (!els.endingDossier) return;
  els.endingDossier.innerHTML = '';
  if (!dossier || !dossier.length) {
    const empty = document.createElement('p');
    empty.className = 'ending-empty';
    empty.textContent = '（尚無故事檔案）';
    els.endingDossier.appendChild(empty);
    return;
  }

  const banner = document.createElement('div');
  banner.className = 'ending-banner';
  banner.textContent =
    endingType === 'TRUE'
      ? '真結局 · 你揭開了各則因果'
      : endingType === 'NORMAL'
        ? '普通結局 · 部分真相仍藏在霧裡'
        : '結局';
  els.endingDossier.appendChild(banner);

  dossier.forEach((item) => {
    const card = document.createElement('article');
    card.className = 'ending-card';
    if (item.truthRevealed) card.classList.add('is-revealed');

    const head = document.createElement('div');
    head.className = 'ending-card-head';
    const title = document.createElement('h3');
    title.textContent = `故事${item.order} · ${item.title || '未命名'}`;
    const badge = document.createElement('span');
    badge.className = 'ending-badge';
    badge.textContent = item.truthRevealed ? '已揭謎' : '未揭謎';
    head.appendChild(title);
    head.appendChild(badge);

    const body = document.createElement('div');
    body.className = 'ending-card-body';
    body.textContent = item.realCaseText || '（尚無說明）';

    card.appendChild(head);
    card.appendChild(body);

    if (item.realCaseUrl) {
      const link = document.createElement('a');
      link.className = 'ending-link';
      link.href = String(item.realCaseUrl);
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.textContent = item.realCaseLabel || '延伸閱讀';
      link.addEventListener('click', (e) => e.stopPropagation());
      card.appendChild(link);
    }

    els.endingDossier.appendChild(card);
  });

  const foot = document.createElement('p');
  foot.className = 'ending-foot';
  foot.textContent =
    '遊戲為真實事件之敘事改編，非法庭紀實。細節請以公開可靠來源為準。';
  els.endingDossier.appendChild(foot);
}

function appendHelperLine(who, text, { streamingLine = false, lineEl = null } = {}) {
  if (streamingLine && lineEl) {
    lineEl.textContent = `${who}：${text}`;
    els.helperLog.scrollTop = els.helperLog.scrollHeight;
    return lineEl;
  }
  const line = document.createElement('div');
  line.textContent = `${who}：${text}`;
  line.style.marginBottom = '6px';
  els.helperLog.appendChild(line);
  els.helperLog.scrollTop = els.helperLog.scrollHeight;
  return line;
}

async function act(fn, busyMessage) {
  if (busy) {
    if (!streaming) setBusy(true, '仍在等待伺服器回應，請稍候…');
    return null;
  }
  setBusy(true, busyMessage || '伺服器處理中，請稍候…');
  try {
    const data = await fn();
    if (data && data.phase) applyState(data);
    return data;
  } catch (e) {
    console.error(e);
    alert(e.message || String(e));
    return null;
  } finally {
    setBusy(false);
  }
}

/** 館長串流回答 */
async function streamNpcAnswer(optionId) {
  if (busy) return;
  streaming = true;
  setBusy(true, '館長（AI）思考中…', { soft: true });
  els.options.classList.add('hidden');
  els.speaker.textContent = '館長';
  els.dialogue.textContent = '（AI思考中…）';
  els.tip.classList.add('hidden');
  setContinueHint(false);
  els.dialogueBox.classList.add('no-advance');
  els.dialogueBox.classList.add('streaming');

  // 思考中：idle；出字後 talk；字出完最多再 5 下後停（點擊可提前停）
  setNpcTalking(false);

  let acc = '';
  let shown = 0; // 已顯示字數（節奏顯示）
  let paceTimer = null;
  let paceDone = null; // 串流結束後等節奏追上的 resolver
  const THINKING = '（AI思考中…）';
  els.dialogue.textContent = THINKING;

  // 節奏顯示：模型 token 先進緩衝，前端以 TYPEWRITER_MS_PER_CHAR 逐字放出
  const settlePace = () => {
    if (paceTimer != null) {
      clearTimeout(paceTimer);
      paceTimer = null;
    }
    if (paceDone) {
      const r = paceDone;
      paceDone = null;
      r();
    }
  };
  const pump = () => {
    if (shown >= acc.length) {
      settlePace();
      return;
    }
    shown += 1;
    els.dialogue.textContent = acc.slice(0, shown);
    if (els.dialogueBox) {
      els.dialogueBox.scrollTop = els.dialogueBox.scrollHeight;
    }
    paceTimer = setTimeout(pump, TYPEWRITER_MS_PER_CHAR);
  };
  streamPaceSkip = () => {
    shown = acc.length;
    els.dialogue.textContent = acc;
    if (els.dialogueBox) {
      els.dialogueBox.scrollTop = els.dialogueBox.scrollHeight;
    }
    settlePace();
  };
  try {
    const done = await api.answerStream(optionId, {
      onMeta: (meta) => {
        if (meta.storyScore != null) {
          els.score.textContent =
            `本則 ${meta.storyScore} · ${meta.currentRound}/${meta.maxRounds}`;
        }
        // 有 token 前維持「思考中」，不要清空對話框
        if (!acc) {
          els.dialogue.textContent = THINKING;
        }
      },
      onToken: (text) => {
        // 第一個 token：開始說話（串流期間持續 talk）
        if (acc.length === 0) {
          setNpcTalking(true);
        }
        acc += text;
        if (paceTimer == null) pump();
      },
      onDone: () => {},
    });

    // 模型已收完，等節奏顯示追上再收尾（點擊可跳過）
    if (shown < acc.length) {
      await new Promise((r) => { paceDone = r; });
    }

    if (done?.data) {
      applyState(done.data, { preserveDialogue: true });
      els.dialogue.textContent = done.fullText || acc || done.data.ui?.npcText || '';
      els.speaker.textContent = '館長';
      // 串流結束後補上可繼續提示
      setContinueHint(true, done.data.phase || 'OPTION_REPLY');
      els.tip.classList.remove('hidden');
      els.dialogueBox.classList.remove('no-advance');
    } else if (acc) {
      els.dialogue.textContent = acc;
      setContinueHint(true, 'OPTION_REPLY');
      els.tip.classList.remove('hidden');
      els.dialogueBox.classList.remove('no-advance');
    }

    // 字出完畢：再 talk 最多 5 下後自動回 idle
    if (npcTalking) {
      scheduleTalkStopAfterBeats(TALK_MAX_BEATS_AFTER_TEXT);
    }
  } catch (e) {
    console.error(e);
    setNpcTalking(false);
    alert(e.message || String(e));
    try {
      const s = await api.state();
      applyState(s);
    } catch { /* ignore */ }
  } finally {
    if (paceTimer != null) {
      clearTimeout(paceTimer);
      paceTimer = null;
    }
    streamPaceSkip = null;
    els.dialogueBox.classList.remove('streaming');
    streaming = false;
    setBusy(false);
  }
}

/** 精靈串流 */
async function streamHelper({ mode, message }) {
  if (busy) return;
  streaming = true;
  setBusy(true, mode === 'hint' ? '精靈（AI）思考中…' : '精靈（AI）回覆中…', { soft: true });
  // 不擋 helper dock 內部顯示
  if (els.helperDock) els.helperDock.classList.remove('is-busy');

  const who = '精靈';
  let acc = '';
  const lineEl = appendHelperLine(who, '（AI思考中…）', {});
  lineEl.style.opacity = '0.9';

  try {
    const handlers = {
      onMeta: () => {
        if (!acc) lineEl.textContent = `${who}：（AI思考中…）`;
      },
      onToken: (text) => {
        if (acc.length === 0) {
          acc = text;
        } else {
          acc += text;
        }
        lineEl.textContent = `${who}：${acc}`;
        lineEl.style.opacity = '1';
        els.helperLog.scrollTop = els.helperLog.scrollHeight;
      },
    };
    const done = mode === 'hint'
      ? await api.helperHintStream(els.hintLevel.value, handlers)
      : await api.helperChatStream(message, els.hintLevel.value, handlers);

    if (done?.fullText) {
      lineEl.textContent = `${who}：${done.fullText}`;
    }
    if (done?.data) {
      applyState(done.data, { preserveDialogue: true });
    }
  } catch (e) {
    console.error(e);
    lineEl.textContent = `${who}：（失敗）${e.message || e}`;
    alert(e.message || String(e));
  } finally {
    streaming = false;
    setBusy(false);
  }
}

async function onDialogueClick(e) {
  if (e) e.stopPropagation();
  // 打字中：先跳過到全文，不進入下一階段
  if (typewriterRunning) {
    stopTypewriter({ complete: true });
    return;
  }
  // AI 串流節奏顯示中：跳過到已收到的全文
  if (streamPaceSkip) {
    streamPaceSkip();
    return;
  }
  if (busy) {
    if (!streaming) setBusy(true, '仍在等待伺服器回應，請稍候…');
    return;
  }
  if (!state) return;
  const p = state.phase;
  if (!ADVANCE_PHASES.has(p)) return;

  // 點擊繼續：結束說話循環，回 idle
  setNpcTalking(false);

  let message = '處理中…';
  let fn;
  if (p === 'INTRO') {
    message = '進入故事選單…';
    fn = () => api.introContinue();
  } else if (p === 'STORY_RESULT') {
    message = '準備提問…';
    fn = () => api.beginQuestions();
  } else if (p === 'OPTION_REPLY') {
    message = '進入下一輪…';
    fn = () => api.continueAfterReply();
  } else if (p === 'STORY_SUMMARY') {
    message = '返回選單…';
    fn = () => api.backToMenu();
  } else if (p === 'ENDING') {
    // 通關動畫：直接播開門影片，播完結算回標題（現實世界）
    if (endingAnimPlaying) return;
    endingAnimPlaying = true;
    playEndingAnimation().finally(() => { endingAnimPlaying = false; });
    return;
  } else {
    return;
  }
  await act(fn, message);
}

let endingAnimPlaying = false;

/** 結算回標題：直接呼叫 API（不經 act，避免 loading 遮罩在影片上閃現） */
async function settleEnding() {
  try {
    const data = await api.endingDone();
    if (data && data.phase) applyState(data);
  } catch (e) {
    console.error('endingDone 失敗', e);
    try {
      const s = await api.state();
      applyState(s);
    } catch { /* ignore */ }
  }
}

/**
 * 通關動畫：播放開門影片（end.webm）→ 播完直接結算 → 影片淡出露出標題。
 */
async function playEndingAnimation() {
  const scene = $('ending-scene');
  const video = $('escene-video');
  if (!scene || !video) { await settleEnding(); return; }
  // 淨空畫面：館長留在圖書館，玩家走向門
  els.npcSprite?.parentElement?.classList.add('npc-hidden');
  els.dialogueBox.classList.add('hidden');
  if (els.menu) els.menu.classList.add('hidden');
  if (els.menuLeft) els.menuLeft.classList.add('hidden');
  if (els.endingPanel) els.endingPanel.classList.add('hidden');
  const btnEnding = $('btn-ending');
  if (btnEnding) btnEnding.classList.add('hidden');
  scene.classList.remove('hidden');

  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  try {
    video.currentTime = 0;
    video.classList.add('show');
    await video.play();
  } catch (e) {
    console.warn('結局影片播放失敗，直接結算', e);
    video.classList.add('show');
  }

  // 等影片播完（含 20 秒保險上限）
  await new Promise((resolve) => {
    let done = false;
    const fin = () => {
      if (done) return;
      done = true;
      resolve();
    };
    video.addEventListener('ended', fin, { once: true });
    setTimeout(fin, 20000);
  });

  await settleEnding();                  // 標題在影片後方就緒
  video.classList.remove('show');        // 影片淡出 → 露出標題
  await wait(1000);
  scene.classList.add('hidden');
  els.dialogueBox.classList.remove('hidden');
}

async function boot() {
  initScale();
  const helperDrag = initHelperDrag(els.helperDock);
  // 預載說話循環，避免首 token 時空白一幀
  const pre = new Image();
  pre.src = NPC_TALK_SRC;

  bindButton($('btn-start'), () => act(() => api.start(true), '開始遊戲…'));
  els.dialogueBox.addEventListener('click', onDialogueClick);

  // 製作・感謝名單：純前端覆蓋層，點背景或按 Esc 關閉
  const creditsPanel = $('credits-panel');
  const closeCredits = () => creditsPanel.classList.add('hidden');
  $('btn-credits').addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    creditsPanel.classList.remove('hidden');
  });
  $('btn-credits-close').addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    closeCredits();
  });
  creditsPanel.addEventListener('click', (e) => {
    if (e.target === creditsPanel) closeCredits();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (!creditsPanel.classList.contains('hidden')) closeCredits();
      const od = $('origin-dialog');
      if (od && !od.classList.contains('hidden')) od.classList.add('hidden');
    }
  });

  // 「聆聽故事起源」Dialog 關閉：按鈕或點背景
  const originDialog = $('origin-dialog');
  if (originDialog) {
    $('btn-origin-close').addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      originDialog.classList.add('hidden');
    });
    originDialog.addEventListener('click', (e) => {
      if (e.target === originDialog) originDialog.classList.add('hidden');
    });
  }

  // 隱藏密碼框（F9）：捷徑完成故事／直接通關
  const cheatDialog = $('cheat-dialog');
  const cheatInput = $('cheat-input');
  const cheatMsg = $('cheat-msg');
  const closeCheat = () => {
    cheatDialog.classList.add('hidden');
    cheatInput.value = '';
    cheatMsg.textContent = '';
    cheatMsg.classList.remove('ok');
  };
  const sendCheat = async () => {
    const code = cheatInput.value.trim();
    if (!code) return;
    cheatMsg.textContent = '……';
    cheatMsg.classList.remove('ok');
    try {
      const data = await api.cheat(code);
      cheatMsg.textContent = data?.ui?.npcText?.slice(0, 40) || '（捷径生效）';
      cheatMsg.classList.add('ok');
      if (data && data.phase) applyState(data);
      setTimeout(closeCheat, 900);
    } catch (e) {
      cheatMsg.textContent = e.message || '密碼不正確';
    }
  };
  document.addEventListener('keydown', (e) => {
    if (e.key === 'F9' || e.key === '`') { // F9 或反引號（`）皆可開關
      e.preventDefault();
      if (cheatDialog.classList.contains('hidden')) {
        cheatDialog.classList.remove('hidden');
        cheatInput.focus();
      } else {
        closeCheat();
      }
    }
    if (e.key === 'Escape' && !cheatDialog.classList.contains('hidden')) closeCheat();
  });
  $('btn-cheat-send').addEventListener('click', (e) => { e.stopPropagation(); sendCheat(); });
  $('btn-cheat-close').addEventListener('click', (e) => { e.stopPropagation(); closeCheat(); });
  cheatDialog.addEventListener('click', (e) => {
    if (e.target === cheatDialog) closeCheat();
  });
  cheatInput.addEventListener('keydown', (e) => {
    e.stopPropagation();
    if (e.key === 'Enter') sendCheat();
  });

  // 連點對話框「館長」名牌 3 下（1.2 秒內）：同 F9 開啟密碼框（滑鼠版後門）
  let nameClicks = 0;
  let nameTimer = null;
  $('speaker-name').addEventListener('click', (e) => {
    e.stopPropagation();
    e.preventDefault();
    nameClicks++;
    clearTimeout(nameTimer);
    nameTimer = setTimeout(() => { nameClicks = 0; }, 1200);
    if (nameClicks >= 3) {
      nameClicks = 0;
      cheatDialog.classList.remove('hidden');
      cheatInput.focus();
    }
  });

  $('helper-toggle').addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    // 剛拖曳完：不開關面板
    if (helperDrag?.wasDraggedRecently()) {
      console.log('[helper] skip click after drag');
      return;
    }
    els.helperPanel.classList.toggle('hidden');
  });

  bindButton($('btn-hint'), () => streamHelper({ mode: 'hint' }));

  function sendHelperChat() {
    if (busy) return;
    const input = $('helper-input');
    const msg = input.value.trim();
    if (!msg) return;
    input.value = '';
    appendHelperLine('你', msg);
    streamHelper({ mode: 'chat', message: msg });
  }

  bindButton($('btn-helper-send'), () => sendHelperChat());

  // 輸入框按 Enter 直接送出（Shift+Enter 不送，保留給未來多行）
  $('helper-input').addEventListener('keydown', (e) => {
    if (e.key !== 'Enter' || e.shiftKey || e.isComposing) return;
    e.preventDefault();
    e.stopPropagation();
    sendHelperChat();
  });

  els.hintLevel.onchange = () =>
    act(() => api.helperSettings(els.hintLevel.value), '更新提示等級…');

  try {
    const data = await api.state();
    applyState(data);
  } catch {
    applyState({
      phase: 'TITLE',
      totalScore: 0,
      ui: { npcText: '點擊「進入遊戲」開始。' },
      menu: [],
    });
  }
}

boot();
