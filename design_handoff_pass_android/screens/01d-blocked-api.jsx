// 01 · WELCOME — blocked: android api
function ScreenWelcomeBlocked_androidapi() {
  const T = React.useContext(ThemeContext);
  const Row = ({ ok, label, value }) => (
    <div style={{display:"flex", alignItems:"center", gap:8, padding:"4px 0", fontSize:10, lineHeight:1.4}}>
      <span style={{color: ok ? T.accent : T.danger, width:10, flexShrink:0, fontWeight:700}}>{ok ? "✓" : "✗"}</span>
      <span style={{color:T.textDim, flex:1}}>{label}</span>
      <span style={{color: ok ? T.accent : T.danger}}>{value}</span>
    </div>
  );

  const checks = [["biometric","enrolled"],["hardware keystore","available"],["android api","34"]];

  return (
    <Phone>
      <div style={{padding:"0 24px", display:"flex", flexDirection:"column", height:"100%", justifyContent:"space-between", paddingBottom:24}}>
        <div style={{paddingTop:40}}>
          <div style={{marginBottom:24}}>
            <div style={{
              width:52, height:52, borderRadius:8,
              background: T.accentDim, border:`1px solid ${T.accentMid}`,
              display:"flex", alignItems:"center", justifyContent:"center",
              marginBottom:20,
              boxShadow:`0 0 24px ${T.accentDim}`,
            }}>
              <svg width="26" height="26" viewBox="0 0 26 26" fill="none">
                <rect x="3" y="11" width="20" height="13" rx="2" stroke={T.accent} strokeWidth="1.5"/>
                <path d="M8 11V8a5 5 0 0 1 10 0v3" stroke={T.accent} strokeWidth="1.5" strokeLinecap="round"/>
                <circle cx="13" cy="17" r="2" fill={T.accent}/>
                <line x1="13" y1="19" x2="13" y2="21" stroke={T.accent} strokeWidth="1.5" strokeLinecap="round"/>
              </svg>
            </div>
            <div style={{color:T.accent, fontSize:22, fontWeight:700, letterSpacing:"-0.02em"}}>pass<span style={{color:T.textDim, fontWeight:300}}>.android</span></div>
            <div style={{color:T.textDim, fontSize:11, marginTop:6, lineHeight:1.6}}>
              the standard unix password<br/>manager — on your phone
            </div>
          </div>

          <div style={{
            background:T.surface, border:`1px solid ${T.danger}55`,
            borderRadius:T.r, padding:"12px 14px",
            boxShadow:`0 0 18px ${T.danger}15`,
          }}>
            <div style={{display:"flex", alignItems:"center", gap:8, marginBottom:8}}>
              <span style={{color:T.textFaint, fontSize:10}}>$</span>
              <span style={{color:T.text, fontSize:10}}>pass --check-device</span>
            </div>
            <div style={{height:1, background:T.border, marginBottom:6}}/>
            {checks.map(([label, ok_value]) => {
              const failing = label === "android api";
              return <Row key={label} ok={!failing} label={label} value={failing ? "< 30" : ok_value}/>;
            })}
            <div style={{height:1, background:T.border, marginTop:6, marginBottom:8}}/>
            <div style={{color:T.danger, fontSize:9, letterSpacing:"0.08em"}}>
              blocked · 1 check failed
            </div>
          </div>

          <div style={{ marginTop:14, color:T.text, fontSize:11, fontWeight:600, marginBottom:6 }}>
            android 11 or newer required
          </div>
          <div style={{ color:T.textDim, fontSize:10, lineHeight:1.6 }}>
            pass.android relies on platform biometric apis introduced in android 11 (api 30). update your system or check with your device manufacturer for an upgrade.
          </div>
        </div>

        <div style={{display:"flex", flexDirection:"column", gap:8}}>
          <Btn full>check for updates →</Btn>
          <div style={{
            background:"transparent",
            border:`1px dashed ${T.border2}`,
            color:T.textFaint,
            borderRadius:T.r, padding:"10px 16px",
            fontSize:11, fontWeight:600, textAlign:"center",
            letterSpacing:"0.05em",
          }}>$ clone a store</div>
          <div style={{textAlign:"center", color:T.textFaint, fontSize:9, marginTop:4}}>
            re-checks automatically when you return
          </div>
        </div>
      </div>
    </Phone>
  );
}
window.ScreenWelcomeBlocked_androidapi = ScreenWelcomeBlocked_androidapi;
