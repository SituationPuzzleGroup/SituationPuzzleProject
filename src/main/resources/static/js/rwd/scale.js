const MIN_W = 960;
const MIN_H = 540;
const DESIGN_W = 960;
const DESIGN_H = 540;

export function initScale() {
  const root = document.getElementById('scale-root');
  const mask = document.getElementById('min-size-mask');

  function apply() {
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    if (vw < MIN_W || vh < MIN_H) {
      mask.classList.remove('hidden');
    } else {
      mask.classList.add('hidden');
    }
    const scale = Math.min(vw / DESIGN_W, vh / DESIGN_H, 1.2);
    root.style.transform = `scale(${scale})`;
  }

  window.addEventListener('resize', apply);
  apply();
}
