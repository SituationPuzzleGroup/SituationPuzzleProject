/**
 * 迴紋針精靈：滑鼠／觸控拖曳，位置存 localStorage。
 * 點擊（幾乎沒移動）→ 不攔截，讓按鈕正常 click 開面板。
 * 拖曳超過門檻 → 移動位置，並抑制誤觸 click。
 */
const STORAGE_KEY = 'sp.helperDock.pos';
const DRAG_THRESHOLD = 8; // px，超過才算拖曳

export function initHelperDrag(dockEl) {
  if (!dockEl) return { wasDraggedRecently: () => false };

  // 還原位置
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
    if (saved && typeof saved.left === 'number' && typeof saved.top === 'number') {
      applyPos(dockEl, saved.left, saved.top);
    }
  } catch {
    /* ignore */
  }

  let tracking = false;
  let dragging = false;
  let startX = 0;
  let startY = 0;
  let origLeft = 0;
  let origTop = 0;
  let pointerId = null;

  const dragHandles = [
    dockEl.querySelector('#helper-toggle'),
    dockEl.querySelector('.helper-head'),
  ].filter(Boolean);

  function onDown(e) {
    if (e.button != null && e.button !== 0) return;
    const t = e.target;
    // 面板內互動元件不開始拖
    if (t.closest('select, input, #btn-hint, #btn-helper-send, #helper-log')) return;
    if (t.closest('#helper-panel') && !t.closest('.helper-head')) return;

    const rect = dockEl.getBoundingClientRect();
    tracking = true;
    dragging = false;
    pointerId = e.pointerId;
    startX = e.clientX;
    startY = e.clientY;
    origLeft = rect.left;
    origTop = rect.top;
    // 注意：這裡不要 preventDefault，否則會吃掉 click，導致點不開面板
  }

  function onMove(e) {
    if (!tracking || (pointerId != null && e.pointerId !== pointerId)) return;
    const dx = e.clientX - startX;
    const dy = e.clientY - startY;

    if (!dragging) {
      if (Math.abs(dx) + Math.abs(dy) < DRAG_THRESHOLD) return;
      // 確認進入拖曳
      dragging = true;
      dockEl.classList.add('dragging');
      dockEl.dataset.suppressClick = '1';
      try {
        dockEl.setPointerCapture(e.pointerId);
      } catch {
        /* ignore */
      }
    }

    let left = origLeft + dx;
    let top = origTop + dy;
    const w = dockEl.offsetWidth || 48;
    const h = dockEl.offsetHeight || 48;
    left = Math.max(0, Math.min(window.innerWidth - Math.min(w, 48), left));
    top = Math.max(0, Math.min(window.innerHeight - Math.min(h, 48), top));
    applyPos(dockEl, left, top);
    e.preventDefault();
  }

  function onUp(e) {
    if (!tracking || (pointerId != null && e.pointerId !== pointerId)) return;
    tracking = false;

    if (dragging) {
      dockEl.classList.remove('dragging');
      try {
        dockEl.releasePointerCapture(e.pointerId);
      } catch {
        /* ignore */
      }
      const rect = dockEl.getBoundingClientRect();
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify({ left: rect.left, top: rect.top }));
      } catch {
        /* ignore */
      }
      // 拖曳後短暫抑制 click（click 通常在 pointerup 之後觸發）
      dockEl.dataset.suppressClick = '1';
      setTimeout(() => {
        delete dockEl.dataset.suppressClick;
      }, 120);
    } else {
      // 純點擊：確保不抑制
      delete dockEl.dataset.suppressClick;
    }
    pointerId = null;
    dragging = false;
  }

  dragHandles.forEach((el) => {
    el.addEventListener('pointerdown', onDown);
  });
  window.addEventListener('pointermove', onMove, { passive: false });
  window.addEventListener('pointerup', onUp);
  window.addEventListener('pointercancel', onUp);

  return {
    wasDraggedRecently: () => dockEl.dataset.suppressClick === '1',
  };
}

function applyPos(el, left, top) {
  el.style.left = `${left}px`;
  el.style.top = `${top}px`;
  el.style.right = 'auto';
  el.style.bottom = 'auto';
}
