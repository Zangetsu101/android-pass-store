function ScreenPassphrase() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <div style={{ display:"flex", flexDirection:"column", height:"100%" }}>
        {/* topbar */}
        <div style={{
          padding:"12px 18px 10px", borderBottom:`1px solid ${T.border}`,
          display:"flex", alignItems:"center", gap:10, flexShrink:0,
        }}>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M9 11L5 7L9 3" stroke={T.textDim} strokeWidth="1.4" strokeLinecap="round"/>
          </svg>
          <div style={{ flex:1 }}>
            <div style={{ color:T.textDim, fontSize:9 }}>email/gmail</div>
            <div style={{ color:T.accent, fontSize:14, fontWeight:700 }}>gmail</div>
          </div>
        </div>

        <div style={{ flex:1, display:"flex", flexDirection:"column", justifyContent:"center", padding:"0 24px 40px" }}>
          {/* lock icon */}
          <div style={{
            width:48, height:48, borderRadius:10,
            background:T.accentDim, border:`1px solid ${T.accentMid}`,
            display:"flex", alignItems:"center", justifyContent:"center",
            margin:"0 auto 20px",
            boxShadow:`0 0 24px ${T.accentDim}`,
          }}>
            <svg width="22" height="22" viewBox="0 0 26 26" fill="none">
              <rect x="3" y="11" width="20" height="13" rx="2" stroke={T.accent} strokeWidth="1.5"/>
              <path d="M8 11V8a5 5 0 0 1 10 0v3" stroke={T.accent} strokeWidth="1.5" strokeLinecap="round"/>
              <circle cx="13" cy="17" r="2" fill={T.accent}/>
              <line x1="13" y1="19" x2="13" y2="21" stroke={T.accent} strokeWidth="1.5" strokeLinecap="round"/>
            </svg>
          </div>

          <div style={{ color:T.accent, fontSize:15, fontWeight:700, textAlign:"center", marginBottom:6 }}>session expired</div>
          <div style={{ color:T.textDim, fontSize:10, textAlign:"center", marginBottom:28, lineHeight:1.6 }}>
            enter your gpg key passphrase<br/>to start a new session
          </div>

          {/* passphrase input */}
          <Label>passphrase</Label>
          <div style={{
            background:T.surface, border:`1px solid ${T.accent}55`,
            borderRadius:T.r, padding:"10px 12px", marginBottom:6,
            display:"flex", alignItems:"center", gap:8,
          }}>
            <span style={{ flex:1, color:T.text, fontSize:13, letterSpacing:"0.2em" }}>••••••••••••</span>
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
              <path d="M6.5 2C3.5 2 1 6.5 1 6.5s2.5 4.5 5.5 4.5 5.5-4.5 5.5-4.5S9.5 2 6.5 2z" stroke={T.textDim} strokeWidth="1.1"/>
              <circle cx="6.5" cy="6.5" r="1.5" fill={T.textDim}/>
            </svg>
          </div>
          <div style={{ color:T.textFaint, fontSize:9, marginBottom:24 }}>
            session lasts 30 min · configurable in settings
          </div>

          <Btn full>unlock session</Btn>
        </div>
      </div>
    </Phone>
  );
}
window.ScreenPassphrase = ScreenPassphrase;
