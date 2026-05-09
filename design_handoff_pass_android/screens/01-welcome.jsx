function ScreenWelcome() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <div style={{padding:"0 24px", display:"flex", flexDirection:"column", height:"100%", justifyContent:"space-between", paddingBottom:24}}>
        {/* top */}
        <div style={{paddingTop:40}}>
          {/* logo mark */}
          <div style={{marginBottom:32}}>
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

          {/* feature list */}
          <div style={{display:"flex", flexDirection:"column", gap:10}}>
            {[
              ["gpg", "end-to-end encrypted with your gpg key"],
              ["git", "syncs with any git remote"],
              ["pass", "100% compatible with unix pass"],
            ].map(([tag, desc]) => (
              <div key={tag} style={{display:"flex", alignItems:"flex-start", gap:10}}>
                <div style={{
                  background:T.accentDim, border:`1px solid ${T.accentMid}`,
                  borderRadius:3, padding:"2px 6px",
                  color:T.accent, fontSize:9, fontWeight:700,
                  letterSpacing:"0.1em", flexShrink:0, marginTop:1,
                }}>{tag}</div>
                <div style={{color:T.textDim, fontSize:11, lineHeight:1.5}}>{desc}</div>
              </div>
            ))}
          </div>
        </div>

        {/* actions */}
        <div style={{display:"flex", flexDirection:"column", gap:8}}>
          <Btn full>$ clone a store</Btn>
          <div style={{textAlign:"center", color:T.textFaint, fontSize:9, marginTop:4}}>
            requires git + gpg key
          </div>
        </div>
      </div>
    </Phone>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// 02 · CLONE REPO
// ─────────────────────────────────────────────────────────────────────────────
window.ScreenWelcome = ScreenWelcome;
