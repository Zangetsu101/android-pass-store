// _settings-base.jsx — shared settings screen with variants
// Props:
//   pullOn {bool}      — pull on open toggle is ON
//   children {string}  — "defaultView" | "clipboard" | "session" (bottom-sheet variant)

function SettingsBase({ pullOn = false, children: variant }) {
  const T = React.useContext(ThemeContext);

  const Toggle = ({ on }) => (
    <div style={{
      width: 32, height: 18, borderRadius: 9,
      background: on ? T.accent : T.border2,
      position: "relative", flexShrink: 0,
      transition: "background 0.2s",
    }}>
      <div style={{
        width: 12, height: 12, borderRadius: "50%", background: "#fff",
        position: "absolute", top: 3,
        left: on ? 17 : 3,
        transition: "left 0.2s",
      }} />
    </div>
  );

  const Row = ({ label, value, danger, static: isStatic, toggle, toggleOn, active }) => (
    <div style={{
      display: "flex", alignItems: "center",
      padding: "11px 14px",
      borderBottom: `1px solid ${T.border}`,
      background: active ? T.accentDim : "transparent",
    }}>
      <span style={{ color: danger ? T.danger : T.text, fontSize: 11, flex: 1 }}>{label}</span>
      {value && <span style={{ color: T.textDim, fontSize: 10, marginRight: 6 }}>{value}</span>}
      {toggle && <Toggle on={toggleOn} />}
      {!danger && !isStatic && !toggle && (
        <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
          <path d="M3.5 8L6.5 5L3.5 2" stroke={active ? T.accent : T.textFaint} strokeWidth="1.2" strokeLinecap="round" />
        </svg>
      )}
    </div>
  );

  // ── Bottom-sheet overlays ─────────────────────────────────────────────────
  const BottomSheet = ({ title, children: content }) => (
    <div style={{
      position: "absolute", bottom: 0, left: 0, right: 0,
      background: T.raised,
      border: `1px solid ${T.border2}`,
      borderRadius: "12px 12px 0 0",
      padding: "0 0 20px",
      zIndex: 20,
      boxShadow: `0 -8px 32px rgba(0,0,0,0.5)`,
    }}>
      {/* drag handle */}
      <div style={{ display: "flex", justifyContent: "center", padding: "10px 0 6px" }}>
        <div style={{ width: 32, height: 3, borderRadius: 2, background: T.border2 }} />
      </div>
      <div style={{
        color: T.textDim, fontSize: 9, letterSpacing: "0.12em",
        textTransform: "uppercase", fontWeight: 600,
        padding: "4px 16px 12px",
        borderBottom: `1px solid ${T.border}`,
      }}>{title}</div>
      {content}
    </div>
  );

  const SheetOption = ({ label, sub, selected }) => (
    <div style={{
      display: "flex", alignItems: "center", padding: "12px 16px",
      borderBottom: `1px solid ${T.border}`,
      background: selected ? T.accentDim : "transparent",
    }}>
      <div style={{ flex: 1 }}>
        <div style={{ color: selected ? T.accent : T.text, fontSize: 11 }}>{label}</div>
        {sub && <div style={{ color: T.textDim, fontSize: 9, marginTop: 2 }}>{sub}</div>}
      </div>
      {selected && (
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
          <path d="M2 6l3 3 5-5" stroke={T.accent} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )}
    </div>
  );

  const TimeOption = ({ val, label, selected }) => (
    <div style={{
      display: "flex", alignItems: "center", justifyContent: "space-between",
      padding: "11px 16px",
      borderBottom: `1px solid ${T.border}`,
      background: selected ? T.accentDim : "transparent",
    }}>
      <span style={{ color: selected ? T.accent : T.text, fontSize: 11 }}>{label}</span>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <span style={{ color: T.textDim, fontSize: 10 }}>{val}</span>
        {selected && (
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <path d="M2 6l3 3 5-5" stroke={T.accent} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        )}
      </div>
    </div>
  );

  // Dim overlay behind bottom sheets
  const Scrim = () => (
    <div style={{
      position: "absolute", inset: 0, zIndex: 19,
      background: "rgba(0,0,0,0.55)",
    }} />
  );

  const hasSheet = !!variant;

  return (
    <Phone>
      <div style={{ display: "flex", flexDirection: "column", height: "100%", position: "relative" }}>
        {/* header */}
        <div style={{
          padding: "12px 18px 10px",
          borderBottom: `1px solid ${T.border}`,
          flexShrink: 0,
        }}>
          <div style={{ color: T.accent, fontSize: 14, fontWeight: 700 }}>settings</div>
        </div>

        {/* scrollable body */}
        <div style={{
          flex: 1, overflowY: "auto", padding: "12px 18px",
          filter: hasSheet ? "blur(1px)" : "none",
          transition: "filter 0.15s",
        }}>
          {/* git */}
          <Label>git</Label>
          <div style={{ background: T.surface, border: `1px solid ${T.border2}`, borderRadius: T.r, overflow: "hidden", marginBottom: 14 }}>
            <Row label="remote url" value="github.com:user/…" static />
            <Row label="ssh key" value="ed25519" static />
            <Row label="pull on open" toggle toggleOn={pullOn} />
          </div>

          {/* gpg */}
          <Label>gpg</Label>
          <div style={{ background: T.surface, border: `1px solid ${T.border2}`, borderRadius: T.r, overflow: "hidden", marginBottom: 14 }}>
            <Row label="key id" value="A3F9 2B1C" static />
            <Row label="uid" value="alice@example.com" static />
          </div>

          {/* display */}
          <Label>display</Label>
          <div style={{ background: T.surface, border: `1px solid ${T.border2}`, borderRadius: T.r, overflow: "hidden", marginBottom: 14 }}>
            <Row label="default view" value="tree" active={variant === "defaultView"} />
            <Row label="clipboard timeout" value="45s" active={variant === "clipboard"} />
            <Row label="session timeout" value="30 min" active={variant === "session"} />
            <Row label="biometric unlock" toggle toggleOn={false} />
          </div>

          {/* store */}
          <Label>store</Label>
          <div style={{ background: T.surface, border: `1px solid ${T.border2}`, borderRadius: T.r, overflow: "hidden", marginBottom: 14 }}>
            <Row label="delete local store" danger />
          </div>

          <div style={{ textAlign: "center", color: T.textFaint, fontSize: 9, marginTop: 4 }}>
            pass.android · v0.1.0 · linux pass compatible
          </div>
        </div>

        {/* ── Bottom sheets ── */}
        {variant === "defaultView" && (
          <>
            <Scrim />
            <BottomSheet title="default view">
              <SheetOption label="tree" sub="hierarchical folder view" selected />
              <SheetOption label="flat" sub="sorted by last accessed" />
            </BottomSheet>
          </>
        )}

        {variant === "clipboard" && (
          <>
            <Scrim />
            <BottomSheet title="clipboard timeout">
              <TimeOption val="15s" label="15 seconds" />
              <TimeOption val="30s" label="30 seconds" />
              <TimeOption val="45s" label="45 seconds" selected />
              <TimeOption val="60s" label="1 minute" />
              <TimeOption val="∞" label="never" />
            </BottomSheet>
          </>
        )}

        {variant === "session" && (
          <>
            <Scrim />
            <BottomSheet title="session timeout">
              <TimeOption val="5 min" label="5 minutes" />
              <TimeOption val="15 min" label="15 minutes" />
              <TimeOption val="30 min" label="30 minutes" selected />
              <TimeOption val="1 hr" label="1 hour" />
              <TimeOption val="∞" label="until app close" />
            </BottomSheet>
          </>
        )}
      </div>
    </Phone>
  );
}

window.SettingsBase = SettingsBase;
