import Icon from './Icon.jsx';
import { ICON_NAMES } from './iconRegistry.jsx';

const MODES = [
  { id: 'dark', title: 'Dark' },
  { id: 'light', title: 'Light' },
];

const SIZES = [16, 20, 24];
const TONES = ['text', 'muted', 'accent', 'warning', 'danger'];

export default function IconShowcase() {
  return (
    <main className="icon-showcase">
      <header className="icon-showcase-header">
        <div>
          <span>LoToTi Icon System · QA</span>
          <h1>Icon Showcase</h1>
          <p>{ICON_NAMES.length} icons · 24×24 · stroke 2.1 · round caps/joins · currentColor</p>
        </div>
        <Icon name="nav_ai" size={30} />
      </header>

      {MODES.map((mode) => (
        <section key={mode.id} className="icon-showcase-block" data-mode={mode.id}>
          <h2>{mode.title}</h2>
          {ICON_NAMES.map((name) => (
            <div className="icon-showcase-row" key={name}>
              <div className="icon-showcase-name"><code>{name}</code></div>
              <div className="icon-showcase-cells">
                {SIZES.map((size) => (
                  <div className="icon-showcase-size" key={size}>
                    {TONES.map((tone) => (
                      <span key={tone} className={`icon-chip tone-${tone}`} title={`${name} · ${size}px · ${tone}`}>
                        <Icon name={name} size={size} />
                      </span>
                    ))}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </section>
      ))}
    </main>
  );
}