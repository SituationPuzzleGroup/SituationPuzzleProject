import { applyStateEnvelope, readHistory, captureDisplay } from './stateStore.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

async function request(method, path, body) {
  const opts = {
    method,
    credentials: 'include',
    headers: body !== undefined ? JSON_HEADERS : {},
  };
  if (body !== undefined) {
    const payload = body ?? {};
    // 無狀態化：每次寫入請求隨帶兩條對話紀錄當 LLM context（GET 無 body 不帶）
    payload._history = readHistory();
    opts.body = JSON.stringify(payload);
  }
  const res = await fetch(path, opts);
  let json;
  try {
    json = await res.json();
  } catch {
    throw new Error('伺服器回應不是 JSON');
  }
  if (!json.ok) {
    const msg = json.error?.message || res.statusText || '請求失敗';
    const err = new Error(msg);
    err.code = json.error?.code;
    err.status = res.status;
    throw err;
  }
  // 無狀態化：套用回應中的進度 envelope（寫 cookie + sessionStorage）+ 捕獲顯示文字
  // 僅「有送 body 的請求」才以回應歷史覆寫 sessionStorage；GET 沒送 _history，
  // server 回顯空歷史，覆寫會清掉前端持有的真值。
  applyStateEnvelope(json.state, { withHistory: body !== undefined });
  captureDisplay(json.data);
  return json.data;
}

/**
 * POST + SSE。handlers 可為 async（會 await），方便前端讓出主執行緒重繪。
 */
async function streamPost(path, body, handlers = {}) {
  console.log('[stream] POST', path);
  const payload = body ?? {};
  payload._history = readHistory();
  const res = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: {
      ...JSON_HEADERS,
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(payload),
  });

  console.log('[stream] status', res.status, res.headers.get('content-type'));
  if (!res.ok) {
    let msg = res.statusText;
    try {
      const j = await res.json();
      msg = j.error?.message || msg;
    } catch { /* ignore */ }
    throw new Error(msg || '串流請求失敗');
  }
  if (!res.body) {
    throw new Error('瀏覽器無法讀取串流 body');
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let finalDone = null;
  let tokenCount = 0;

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    buffer = buffer.replace(/\r\n/g, '\n');

    let sep;
    while ((sep = buffer.indexOf('\n\n')) >= 0) {
      const rawEvent = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      const dataLines = rawEvent
        .split('\n')
        .filter((l) => l.startsWith('data:'))
        .map((l) => l.replace(/^data:\s?/, ''));
      if (!dataLines.length) continue;
      const dataStr = dataLines.join('\n');
      let payload;
      try {
        payload = JSON.parse(dataStr);
      } catch {
        continue;
      }
      const type = payload.type;
      if (type === 'meta') {
        console.log('[stream] meta', payload);
        if (handlers.onMeta) await handlers.onMeta(payload);
      } else if (type === 'token') {
        tokenCount += 1;
        // 避免每個 token 都 console.log 造成卡頓
        if (handlers.onToken) await handlers.onToken(payload.text || '', payload);
        // 讓瀏覽器有機會重繪
        await new Promise((r) => requestAnimationFrame(() => r()));
      } else if (type === 'done') {
        console.log('[stream] done tokens=', tokenCount);
        // 無狀態化：SSE 無法寫 Set-Cookie，進度隨 done event 回前端，由 JS 寫回
        applyStateEnvelope(payload.state);
        captureDisplay(payload.data);
        finalDone = payload;
        if (handlers.onDone) await handlers.onDone(payload);
      } else if (type === 'error') {
        console.error('[stream] error', payload);
        const err = new Error(payload.message || '串流錯誤');
        err.code = payload.code;
        if (handlers.onError) await handlers.onError(err);
        throw err;
      }
    }
  }
  console.log('[stream] finished', !!finalDone, 'tokens', tokenCount);
  return finalDone;
}

export const api = {
  health: () => request('GET', '/api/health'),
  start: (reset = false) => request('POST', '/api/game/start', { reset }),
  state: () => request('GET', '/api/game/state'),
  cheat: (code) => request('POST', '/api/game/cheat', { code }),
  introContinue: () => request('POST', '/api/game/intro/continue', {}),
  finish: () => request('POST', '/api/game/finish', {}),
  endingDone: () => request('POST', '/api/game/ending/done', {}),
  selectStory: (storyOrder) => request('POST', '/api/stories/select', { storyOrder }),
  beginQuestions: () => request('POST', '/api/stories/current/begin-questions', {}),
  answer: (optionId) => request('POST', '/api/stories/current/answer', { optionId }),
  answerStream: (optionId, handlers) =>
    streamPost('/api/stories/current/answer/stream', { optionId }, handlers),
  continueAfterReply: () => request('POST', '/api/stories/current/continue', {}),
  finalize: () => request('POST', '/api/stories/current/finalize', {}),
  backToMenu: () => request('POST', '/api/stories/current/back-to-menu', {}),
  realCase: (storyOrder) => request('GET', `/api/stories/${storyOrder}/real-case`),
  helperSettings: (hintLevel) => request('POST', '/api/ai/helper/settings', { hintLevel }),
  helperChat: (message, hintLevel) =>
    request('POST', '/api/ai/helper/chat', { message, hintLevel }),
  helperHint: (hintLevel) => request('POST', '/api/ai/helper/hint', { hintLevel }),
  helperChatStream: (message, hintLevel, handlers) =>
    streamPost('/api/ai/helper/chat/stream', { message, hintLevel }, handlers),
  helperHintStream: (hintLevel, handlers) =>
    streamPost('/api/ai/helper/hint/stream', { hintLevel }, handlers),
};
