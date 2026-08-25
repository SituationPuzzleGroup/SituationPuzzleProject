/**
 * 背景音樂控制（PeriTune 素材，皆為迴圈設計）
 *
 * 對應規則（2026-08-25 定案，同日修訂）：
 *  - 進入遊戲後（INTRO～STORY_MENU 等非故事階段）：Forgotten_Past
 *  - 故事 1：Nightmare_loop
 *  - 故事 2：Coppelia_Room_loop
 *  - 故事 3：Silent_Witness_loop
 *  - 故事 4：Wacky_Witnes_loop
 *  - loop 版以 HTMLAudioElement.loop 無縫循環（素材本身即 loop 剪輯）
 *
 * 預載：preloadAll() 以 fetch 把全部曲目下載成 Blob URL 再播放，
 * 避免遊戲中首次切曲時才開始下載造成卡頓；進度以 onProgress 回報。
 *
 * 瀏覽器自動播放政策：AudioContext/播放必須在使用者互動後啟動，
 * 故首次 startBgm() 於「進入遊戲」按鈕的 click 流程內呼叫。
 */

const TRACKS = {
  ambient: '/audio/bgm/PerituneMaterial_Forgotten_Past.ogg',
  story1: '/audio/bgm/PerituneMaterial_Nightmare_loop.ogg',
  story2: '/audio/bgm/PerituneMaterial_Coppelia_Room_loop.ogg',
  story3: '/audio/bgm/Peritune_Silent_Witness_loop.ogg',
  story4: '/audio/bgm/Peritune_Wacky_Witnes_loop.ogg',
};

/** key → 已下載的 blob: URL（未預載完成的曲目回退原路徑） */
const blobUrls = new Map();

/** @type {HTMLAudioElement | null} */
let audio = null;
/** 目前播放中的曲目 key（同一首不重播） */
let currentKey = null;
/** 使用者靜音偏好；預設播放 */
let muted = false;
/** 首次播放是否已被瀏覽器阻擋（等待下次互動重試） */
let pendingPlay = null;
/** 預載是否已完成（重玩時不重複下載） */
let preloaded = false;

function trackUrl(key) {
  return blobUrls.get(key) || TRACKS[key];
}

function ensureAudio() {
  if (audio) return audio;
  audio = new Audio();
  audio.loop = true;
  audio.preload = 'auto';
  audio.volume = 0.5;
  audio.addEventListener('error', (e) => {
    console.warn('[bgm] 音檔載入失敗', e);
  });
  return audio;
}

/**
 * 預載全部曲目。
 * 逐一下載（非並行）讓進度條平滑前進，也避免瞬間佔滿頻寬。
 * @param {(loaded:number, total:number, name:string) => void} [onProgress]
 * @returns {Promise<void>} 全部完成（或既已預載）後 resolve
 */
async function preloadAllBgm(onProgress) {
  if (preloaded) return;
  const keys = Object.keys(TRACKS);
  let done = 0;
  for (const key of keys) {
    if (!blobUrls.has(key)) {
      try {
        const res = await fetch(TRACKS[key]);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const blob = await res.blob();
        blobUrls.set(key, URL.createObjectURL(blob));
      } catch (e) {
        // 預載失敗不擋遊戲：播放時回退原路徑串流
        console.warn('[bgm] 預載失敗，播放時回退串流', key, e);
      }
    }
    done += 1;
    if (onProgress) onProgress(done, keys.length, TRACKS[key].split('/').pop());
  }
  preloaded = true;
}

/**
 * 切換曲目；同曲不重播、不中斷。
 * @param {'ambient'|'story1'|'story2'|'story3'|'story4'} key
 */
function playBgm(key) {
  if (!TRACKS[key]) return;
  const a = ensureAudio();
  if (currentKey === key) {
    // 同一首：若先前被自動播放政策擋下，趁互動中補播
    if (a.paused) tryResume();
    return;
  }
  currentKey = key;
  a.src = trackUrl(key);
  a.muted = muted;
  a.play().catch(() => {
    // 被瀏覽器自動播放政策阻擋：記下來，任一下次互動再啟動
    pendingPlay = key;
    console.info('[bgm] 等待使用者互動後播放', key);
  });
}

function tryResume() {
  const a = ensureAudio();
  a.play().catch(() => { /* 仍被阻擋，靜默等待 */ });
}

/** 停止播放（回到標題／重置遊戲時） */
function stopBgm() {
  if (!audio) return;
  audio.pause();
  currentKey = null;
  pendingPlay = null;
}

/** 任何使用者互動時呼叫：若先前被阻擋，趁機補播 */
function bgmOnUserGesture() {
  if (pendingPlay != null) {
    const key = pendingPlay;
    pendingPlay = null;
    playBgm(key);
  }
}

/** 依遊戲階段決定曲目 */
function bgmForPhase(phase, currentStoryOrder) {
  const inStory =
    phase === 'STORY_RESULT' ||
    phase === 'ASK_PROMPT' ||
    phase === 'SELECT_OPTION' ||
    phase === 'OPTION_REPLY' ||
    phase === 'STORY_SUMMARY';
  if (inStory) {
    const order = Number(currentStoryOrder) || 0;
    if (order >= 1 && order <= 4) return `story${order}`;
    return 'ambient';
  }
  // 遊戲內行進階段（選單、開場、離館、結局）都用 ambient
  return 'ambient';
}

/** 靜音切換；回傳切換後狀態 */
function toggleMuteBgm() {
  muted = !muted;
  if (audio) audio.muted = muted;
  return muted;
}

export const bgm = {
  play: playBgm,
  stop: stopBgm,
  forPhase: bgmForPhase,
  onUserGesture: bgmOnUserGesture,
  toggleMute: toggleMuteBgm,
  preloadAll: preloadAllBgm,
};
